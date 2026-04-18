const express = require('express');
const path = require('path');
const fs = require('fs');
const https = require('https');
const { execSync, exec } = require('child_process');
const crypto = require('crypto');

const app = express();
const PORT = 5000;

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json({ limit: '10mb' }));

// ── Job store (in-memory) ──────────────────────────────────────────────────
const jobs = new Map();

// ── OpenAI helper ─────────────────────────────────────────────────────────
async function openaiChat(messages, model = 'gpt-4o-mini', temperature = 0.2) {
    const token = process.env.OPENAI_API_KEY;
    if (!token) throw new Error('OPENAI_API_KEY is not set');
    const body = JSON.stringify({ model, messages, temperature, max_tokens: 4096 });
    return new Promise((resolve, reject) => {
        const opts = {
            hostname: 'api.openai.com',
            path: '/v1/chat/completions',
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(body)
            }
        };
        const req = https.request(opts, r => {
            let d = '';
            r.on('data', c => d += c);
            r.on('end', () => {
                try {
                    const parsed = JSON.parse(d);
                    if (parsed.error) reject(new Error(parsed.error.message || JSON.stringify(parsed.error)));
                    else resolve(parsed);
                } catch (e) { reject(e); }
            });
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

// ── Code block extractor ───────────────────────────────────────────────────
function extractCodeBlocks(text) {
    const blocks = [];
    const fenced = /```(\w*)\n?([\s\S]*?)```/g;
    let m;
    while ((m = fenced.exec(text)) !== null) {
        blocks.push({ lang: m[1].toLowerCase() || 'text', code: m[2].trim() });
    }
    if (blocks.length === 0 && text.trim().length > 20) {
        blocks.push({ lang: 'text', code: text.trim() });
    }
    return blocks;
}

// ── Syntax / error validators ──────────────────────────────────────────────
function validateJavaScript(code) {
    const errors = [];
    try {
        const acorn = require('acorn');
        acorn.parse(code, { ecmaVersion: 2022, sourceType: 'module' });
    } catch (e) {
        errors.push({ line: e.loc?.line, col: e.loc?.column, message: e.message });
    }
    return errors;
}

function validatePython(code) {
    const errors = [];
    try {
        const result = execSync(`python3 -c "import ast, sys; ast.parse(sys.stdin.read())"`,
            { input: code, encoding: 'utf8', timeout: 5000, stdio: ['pipe', 'pipe', 'pipe'] });
    } catch (e) {
        const stderr = e.stderr || e.message || '';
        const match = stderr.match(/line (\d+)/);
        errors.push({ line: match ? parseInt(match[1]) : null, message: stderr.trim() });
    }
    return errors;
}

function validateKotlin(code) {
    const errors = [];
    const bracketBalance = (code.match(/\{/g) || []).length - (code.match(/\}/g) || []).length;
    const parenBalance = (code.match(/\(/g) || []).length - (code.match(/\)/g) || []).length;
    if (bracketBalance !== 0) errors.push({ message: `Mismatched curly braces (${bracketBalance > 0 ? '+' : ''}${bracketBalance})` });
    if (parenBalance !== 0) errors.push({ message: `Mismatched parentheses (${parenBalance > 0 ? '+' : ''}${parenBalance})` });
    if (!/\bfun\s+\w+/.test(code) && !/\bclass\s+\w+/.test(code) && code.length > 100)
        errors.push({ message: 'No function or class definition detected — may be incomplete' });
    return errors;
}

function validateHtml(code) {
    const errors = [];
    const selfClosing = new Set(['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr']);
    const openTags = [];
    const tagRe = /<\/?([a-zA-Z][a-zA-Z0-9-]*)[^>]*>/g;
    let m;
    while ((m = tagRe.exec(code)) !== null) {
        const full = m[0], tag = m[1].toLowerCase();
        if (selfClosing.has(tag) || full.endsWith('/>')) continue;
        if (full.startsWith('</')) {
            if (openTags.length && openTags[openTags.length - 1] === tag) openTags.pop();
            else errors.push({ message: `Unexpected closing tag </${tag}>` });
        } else {
            openTags.push(tag);
        }
    }
    openTags.forEach(t => errors.push({ message: `Unclosed tag <${t}>` }));
    return errors;
}

function validateCSS(code) {
    const errors = [];
    const open = (code.match(/\{/g) || []).length;
    const close = (code.match(/\}/g) || []).length;
    if (open !== close) errors.push({ message: `Mismatched braces: ${open} open vs ${close} close` });
    return errors;
}

function validateCode(lang, code) {
    const L = lang.toLowerCase();
    if (L === 'javascript' || L === 'js' || L === 'node' || L === 'jsx') return validateJavaScript(code);
    if (L === 'python' || L === 'py') return validatePython(code);
    if (L === 'kotlin' || L === 'kt') return validateKotlin(code);
    if (L === 'html') return validateHtml(code);
    if (L === 'css' || L === 'scss') return validateCSS(code);
    return [];
}

// ── System prompt ──────────────────────────────────────────────────────────
function buildSystemPrompt(language, task) {
    return `You are an expert ${language} developer working inside the Discordia Terminal IDE (an Android-native VS Code-like environment).

Your job is to produce COMPLETE, CORRECT, and WORKING code — no placeholders, no "TODO", no "...".

Rules:
1. Always wrap ALL code in a single fenced code block: \`\`\`${language.toLowerCase()}\n...\n\`\`\`
2. Output ONLY the code block (and a very brief explanation after it if needed).
3. Code must be complete — no truncation, no ellipsis, no partial implementations.
4. Handle edge cases and errors explicitly in the code.
5. If you receive error feedback, fix EVERY error listed before responding.

Task: ${task}`;
}

function buildFixPrompt(errors, code, iteration) {
    const errList = errors.map((e, i) => `  ${i + 1}. ${e.line ? `Line ${e.line}: ` : ''}${e.message}`).join('\n');
    return `Iteration ${iteration} — the code has errors that must be fixed:\n\n${errList}\n\nFaulty code:\n\`\`\`\n${code}\n\`\`\`\n\nProvide the COMPLETE corrected code with ALL errors fixed. Do not truncate.`;
}

// ── SSE broadcaster ────────────────────────────────────────────────────────
function pushEvent(jobId, event, data) {
    const job = jobs.get(jobId);
    if (!job) return;
    job.events.push({ event, data, ts: Date.now() });
    job.listeners.forEach(send => send(event, data));
}

// ── Main generation loop ───────────────────────────────────────────────────
async function runGenerationJob(jobId, task, language, maxIterations) {
    const job = jobs.get(jobId);
    job.status = 'running';

    const emit = (type, payload) => pushEvent(jobId, type, payload);

    emit('start', { task, language, maxIterations });
    emit('log', { text: `Starting code generation for: ${task}`, level: 'info' });

    const messages = [
        { role: 'system', content: buildSystemPrompt(language, task) },
        { role: 'user', content: `Generate the complete ${language} code for: ${task}` }
    ];

    let finalCode = null;
    let finalLang = language;
    let iteration = 0;
    let lastErrors = [];

    try {
        while (iteration < maxIterations) {
            iteration++;
            emit('iteration', { n: iteration, max: maxIterations, phase: 'generating' });
            emit('log', { text: `Iteration ${iteration}/${maxIterations}: Asking AI to generate code...`, level: 'info' });

            const response = await openaiChat(messages, 'gpt-4o-mini', 0.2);
            const assistantMsg = response.choices?.[0]?.message?.content || '';
            messages.push({ role: 'assistant', content: assistantMsg });

            emit('log', { text: `AI responded (${assistantMsg.length} chars). Extracting code blocks...`, level: 'info' });

            const blocks = extractCodeBlocks(assistantMsg);
            if (blocks.length === 0) {
                emit('log', { text: 'No code block found in response — retrying with stricter prompt.', level: 'warn' });
                messages.push({ role: 'user', content: `You did not include a fenced code block. Please provide the COMPLETE code wrapped in \`\`\`${language.toLowerCase()}\\n...\\n\`\`\`.` });
                continue;
            }

            const block = blocks[0];
            const code = block.code;
            const lang = block.lang !== 'text' ? block.lang : language.toLowerCase();
            finalCode = code;
            finalLang = lang;

            emit('code_draft', { iteration, code, lang });
            emit('log', { text: `Code extracted (${code.split('\n').length} lines). Validating...`, level: 'info' });
            emit('iteration', { n: iteration, max: maxIterations, phase: 'validating' });

            const errors = validateCode(lang, code);

            if (errors.length === 0) {
                emit('log', { text: `No errors detected. Code is valid!`, level: 'success' });
                emit('validation_pass', { iteration });
                break;
            }

            lastErrors = errors;
            emit('log', { text: `Found ${errors.length} error(s):`, level: 'error' });
            errors.forEach(e => emit('log', { text: `  → ${e.line ? `Line ${e.line}: ` : ''}${e.message}`, level: 'error' }));
            emit('validation_fail', { iteration, errors });

            if (iteration >= maxIterations) {
                emit('log', { text: `Max iterations reached. Returning best available code with ${errors.length} remaining issue(s).`, level: 'warn' });
                break;
            }

            emit('log', { text: `Sending errors back to AI for correction...`, level: 'info' });
            emit('iteration', { n: iteration, max: maxIterations, phase: 'fixing' });
            messages.push({ role: 'user', content: buildFixPrompt(errors, code, iteration) });
        }

        job.status = 'done';
        job.result = { code: finalCode, lang: finalLang, iterations: iteration, errors: lastErrors };
        emit('done', { code: finalCode, lang: finalLang, iterations: iteration, remainingErrors: lastErrors });
        emit('log', { text: `Generation complete after ${iteration} iteration(s).`, level: 'success' });

    } catch (err) {
        job.status = 'error';
        job.error = err.message;
        emit('error', { message: err.message });
        emit('log', { text: `Fatal error: ${err.message}`, level: 'error' });
    }
}

// ── API: Start a generation job ────────────────────────────────────────────
app.post('/api/generate', (req, res) => {
    const { task, language = 'JavaScript', maxIterations = 8 } = req.body;
    if (!task || task.trim().length < 3) {
        return res.status(400).json({ error: 'task is required (min 3 chars)' });
    }
    const jobId = crypto.randomUUID();
    jobs.set(jobId, {
        id: jobId, status: 'queued', events: [], listeners: new Set(),
        result: null, error: null,
        task, language, maxIterations: Math.min(Math.max(parseInt(maxIterations) || 8, 2), 15)
    });
    runGenerationJob(jobId, task.trim(), language, Math.min(Math.max(parseInt(maxIterations) || 8, 2), 15));
    res.json({ jobId });
});

// ── API: SSE stream for job updates ───────────────────────────────────────
app.get('/api/stream/:jobId', (req, res) => {
    const job = jobs.get(req.params.jobId);
    if (!job) return res.status(404).json({ error: 'job not found' });

    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');
    res.flushHeaders();

    const sendEvent = (event, data) => {
        res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    };

    job.events.forEach(e => sendEvent(e.event, e.data));

    if (job.status === 'done' || job.status === 'error') {
        res.end();
        return;
    }

    job.listeners.add(sendEvent);
    req.on('close', () => job.listeners.delete(sendEvent));
});

// ── API: Poll job status ───────────────────────────────────────────────────
app.get('/api/job/:jobId', (req, res) => {
    const job = jobs.get(req.params.jobId);
    if (!job) return res.status(404).json({ error: 'job not found' });
    res.json({
        id: job.id, status: job.status,
        result: job.result, error: job.error,
        task: job.task, language: job.language
    });
});

// ── GitHub helpers ─────────────────────────────────────────────────────────
function ghFetchFactory(token) {
    return function ghFetch(url, opts = {}) {
        return new Promise((resolve, reject) => {
            const u = new URL(url);
            const options = {
                hostname: u.hostname, path: u.pathname + u.search,
                method: opts.method || 'GET',
                headers: {
                    'Authorization': `token ${token}`, 'User-Agent': 'DiscordiaTerminal',
                    'Accept': 'application/vnd.github.v3+json', 'Content-Type': 'application/json',
                    ...(opts.headers || {})
                }
            };
            const req = https.request(options, r => {
                let d = '';
                r.on('data', c => d += c);
                r.on('end', () => { try { resolve(JSON.parse(d)); } catch (e) { resolve({ error: d }); } });
            });
            req.on('error', reject);
            if (opts.body) req.write(typeof opts.body === 'string' ? opts.body : JSON.stringify(opts.body));
            req.end();
        });
    };
}

// ── GitHub: Push files to repo ─────────────────────────────────────────────
app.post('/_push2', async (req, res) => {
    try {
        const { owner, repo, branch, message, files } = req.body;
        const token = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
        if (!token) return res.status(401).json({ error: 'GITHUB_PERSONAL_ACCESS_TOKEN not set' });

        const base = `https://api.github.com/repos/${owner}/${repo}`;
        const ghFetch = ghFetchFactory(token);

        const refData = await ghFetch(`${base}/git/ref/heads/${branch}`);
        const baseSha = refData.object?.sha;
        if (!baseSha) return res.json({ error: 'branch not found', detail: refData });

        const commitData = await ghFetch(`${base}/git/commits/${baseSha}`);
        const baseTreeSha = commitData.tree?.sha;

        const treeItems = [];
        for (const f of files) {
            const blob = await ghFetch(`${base}/git/blobs`, {
                method: 'POST',
                body: { content: f.content, encoding: 'utf-8' }
            });
            treeItems.push({ path: f.path, mode: '100644', type: 'blob', sha: blob.sha });
        }

        const newTree = await ghFetch(`${base}/git/trees`, {
            method: 'POST',
            body: { base_tree: baseTreeSha, tree: treeItems }
        });

        const newCommit = await ghFetch(`${base}/git/commits`, {
            method: 'POST',
            body: { message, tree: newTree.sha, parents: [baseSha] }
        });

        const updateRef = await ghFetch(`${base}/git/refs/heads/${branch}`, {
            method: 'PATCH',
            body: { sha: newCommit.sha }
        });

        res.json({ ok: true, commit: newCommit.sha, ref: updateRef });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ── Git: commit and push ───────────────────────────────────────────────────
app.post('/api/git/push', async (req, res) => {
    const { message = 'update', branch = 'main', addAll = true } = req.body;
    const token = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
    if (!token) return res.status(401).json({ error: 'GITHUB_PERSONAL_ACCESS_TOKEN not set' });

    // Build authenticated remote URL from existing origin
    let remoteUrl = '';
    try {
        remoteUrl = execSync('git remote get-url origin', { encoding: 'utf8' }).trim();
    } catch (e) {
        return res.status(500).json({ error: 'Could not get git remote: ' + e.message });
    }

    // Inject token into HTTPS URL
    const authedUrl = remoteUrl.replace('https://', `https://${token}@`);

    const steps = [];
    const run = (cmd, label) => {
        try {
            const out = execSync(cmd, { encoding: 'utf8', cwd: process.cwd(), timeout: 30000 }).trim();
            steps.push({ step: label, ok: true, output: out || '(no output)' });
            return true;
        } catch (e) {
            const msg = (e.stderr || e.stdout || e.message || '').trim();
            steps.push({ step: label, ok: false, output: msg });
            return false;
        }
    };

    if (addAll) run('git add .', 'git add .');

    // Skip commit if nothing staged
    const statusOut = execSync('git status --porcelain', { encoding: 'utf8' }).trim();
    if (!statusOut && addAll) {
        steps.push({ step: 'git commit', ok: false, output: 'Nothing to commit — working tree clean' });
        return res.json({ ok: false, steps });
    }

    const safeMsg = message.replace(/"/g, '\\"');
    const committed = run(`git commit -m "${safeMsg}"`, `git commit -m "${safeMsg}"`);
    if (!committed) return res.json({ ok: false, steps });

    const pushed = run(`git push "${authedUrl}" ${branch}`, `git push origin ${branch}`);
    res.json({ ok: pushed, steps });
});

// ── Git: status ─────────────────────────────────────────────────────────────
app.get('/api/git/status', (req, res) => {
    try {
        const status = execSync('git status --short', { encoding: 'utf8', timeout: 8000 }).trim();
        const branch = execSync('git rev-parse --abbrev-ref HEAD', { encoding: 'utf8', timeout: 5000 }).trim();
        const log = execSync('git log --oneline -5', { encoding: 'utf8', timeout: 8000 }).trim();
        res.json({ ok: true, status: status || '(clean)', branch, log });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ── Health check ───────────────────────────────────────────────────────────
app.get('/api/health', (req, res) => {
    let branch = 'unknown';
    try { branch = execSync('git rev-parse --abbrev-ref HEAD', { encoding: 'utf8', timeout: 5000 }).trim(); } catch (_) {}
    res.json({
        ok: true,
        openai: !!process.env.OPENAI_API_KEY,
        github: !!process.env.GITHUB_PERSONAL_ACCESS_TOKEN,
        branch
    });
});

app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Discordia Terminal server running on http://0.0.0.0:${PORT}`);
    console.log(`  OpenAI: ${process.env.OPENAI_API_KEY ? 'configured' : 'NOT SET'}`);
    console.log(`  GitHub: ${process.env.GITHUB_PERSONAL_ACCESS_TOKEN ? 'configured' : 'NOT SET'}`);
});

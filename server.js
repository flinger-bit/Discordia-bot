const express = require('express');
const path = require('path');
const fs = require('fs');
const https = require('https');

const app = express();
const PORT = 5000;

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json({limit:'10mb'}));

function ghGet(apiPath) {
    return new Promise((resolve, reject) => {
        const token = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
        const opts = {
            hostname: 'api.github.com',
            path: apiPath,
            headers: { 'Authorization': `token ${token}`, 'User-Agent': 'DiscordiaTerminal', 'Accept': 'application/vnd.github.v3+json' }
        };
        https.get(opts, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => { try { resolve(JSON.parse(d)); } catch(e) { resolve({ error: d }); } }); }).on('error', reject);
    });
}

function ghPut(apiPath, body) {
    return new Promise((resolve, reject) => {
        const token = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
        const data = JSON.stringify(body);
        const opts = {
            hostname: 'api.github.com', path: apiPath, method: 'PUT',
            headers: { 'Authorization': `token ${token}`, 'User-Agent': 'DiscordiaTerminal', 'Accept': 'application/vnd.github.v3+json', 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }
        };
        const req = https.request(opts, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => { try { resolve(JSON.parse(d)); } catch(e) { resolve({ error: d }); } }); });
        req.on('error', reject); req.write(data); req.end();
    });
}

// Push one or more files to GitHub in a single commit via the Trees/Commits API
app.post('/_push2', async (req, res) => {
    try {
        const { owner, repo, branch, message, files } = req.body;
        const base = `https://api.github.com/repos/${owner}/${repo}`;
        const token = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
        const ghFetch = (url, opts = {}) => {
            return new Promise((resolve, reject) => {
                const u = new URL(url);
                const options = {
                    hostname: u.hostname, path: u.pathname + u.search,
                    method: opts.method || 'GET',
                    headers: { 'Authorization': `token ${token}`, 'User-Agent': 'DiscordiaTerminal', 'Accept': 'application/vnd.github.v3+json', 'Content-Type': 'application/json', ...(opts.headers || {}) }
                };
                const req2 = https.request(options, r => { let d = ''; r.on('data', c => d += c); r.on('end', () => { try { resolve(JSON.parse(d)); } catch(e) { resolve({ error: d }); } }); });
                req2.on('error', reject);
                if (opts.body) req2.write(typeof opts.body === 'string' ? opts.body : JSON.stringify(opts.body));
                req2.end();
            });
        };

        // 1. Get current commit SHA
        const refData = await ghFetch(`${base}/git/ref/heads/${branch}`);
        const baseSha = refData.object?.sha;
        if (!baseSha) { res.json({ error: 'branch not found', detail: refData }); return; }

        // 2. Get base tree SHA
        const commitData = await ghFetch(`${base}/git/commits/${baseSha}`);
        const baseTreeSha = commitData.tree?.sha;

        // 3. Create blobs for each file
        const treeItems = [];
        for (const f of files) {
            const blob = await ghFetch(`${base}/git/blobs`, {
                method: 'POST',
                body: { content: f.content, encoding: 'utf-8' }
            });
            treeItems.push({ path: f.path, mode: '100644', type: 'blob', sha: blob.sha });
        }

        // 4. Create a new tree
        const newTree = await ghFetch(`${base}/git/trees`, {
            method: 'POST',
            body: { base_tree: baseTreeSha, tree: treeItems }
        });

        // 5. Create a commit
        const newCommit = await ghFetch(`${base}/git/commits`, {
            method: 'POST',
            body: { message, tree: newTree.sha, parents: [baseSha] }
        });

        // 6. Update the ref
        const updateRef = await ghFetch(`${base}/git/refs/heads/${branch}`, {
            method: 'PATCH',
            body: { sha: newCommit.sha }
        });

        res.json({ ok: true, commit: newCommit.sha, ref: updateRef });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Discordia Terminal project server running on http://0.0.0.0:${PORT}`);
});

#include "BotManager.hpp"
#include <fstream>
#include <matjson.hpp>

using namespace geode::prelude;

// ── Singleton ─────────────────────────────────────────────────────────────

BotManager* BotManager::get() {
    static BotManager s_instance;
    return &s_instance;
}

// ── State ─────────────────────────────────────────────────────────────────

void BotManager::setState(BotState state) {
    m_state = state;
    if (state == BotState::Recording) {
        m_macro.clear();
        m_currentFrame = 0;
    } else if (state == BotState::Playing) {
        m_currentFrame = 0;
    }
}

std::string BotManager::stateLabel() const {
    switch (m_state) {
        case BotState::Disabled:  return "OFF";
        case BotState::Recording: return "REC";
        case BotState::Playing:   return "PLAY";
    }
    return "?";
}

// ── Recording ─────────────────────────────────────────────────────────────

void BotManager::recordInput(bool player1, bool hold, int button) {
    // Deduplicate: skip if identical frame+player+hold+button already exists
    for (auto& f : m_macro) {
        if (f.frame == m_currentFrame && f.player1 == player1 &&
            f.hold == hold && f.button == button) return;
    }
    m_macro.push_back({ m_currentFrame, player1, hold, button });
}

// ── Playback ──────────────────────────────────────────────────────────────

std::vector<InputFrame> BotManager::getInputsForFrame(int frame, bool player1) const {
    std::vector<InputFrame> result;
    for (const auto& inp : m_macro) {
        if (inp.frame == frame && inp.player1 == player1) {
            result.push_back(inp);
        }
    }
    return result;
}

bool BotManager::isPlaybackFinished() const {
    if (m_macro.empty()) return true;
    int lastFrame = 0;
    for (const auto& inp : m_macro) {
        if (inp.frame > lastFrame) lastFrame = inp.frame;
    }
    return m_currentFrame > lastFrame + 120; // 0.5s extra buffer
}

// ── File I/O ──────────────────────────────────────────────────────────────

std::filesystem::path BotManager::getMacroDir() const {
    auto dir = Mod::get()->getSaveDir() / "macros";
    if (!std::filesystem::exists(dir)) {
        std::filesystem::create_directories(dir);
    }
    return dir;
}

std::filesystem::path BotManager::getMacroPath(const std::string& name) const {
    return getMacroDir() / (name + ".xbot");
}

bool BotManager::saveMacro(const std::string& name) {
    try {
        matjson::Value root = matjson::Value::object();
        root["version"] = 1;
        root["author"]  = Mod::get()->getDeveloper();
        root["frames"]  = (int)m_macro.size();

        matjson::Value inputs = matjson::Value::array();
        for (const auto& inp : m_macro) {
            matjson::Value entry = matjson::Value::object();
            entry["f"]  = inp.frame;
            entry["p1"] = inp.player1;
            entry["h"]  = inp.hold;
            entry["b"]  = inp.button;
            inputs.push(std::move(entry));
        }
        root["inputs"] = std::move(inputs);

        std::ofstream file(getMacroPath(name));
        if (!file.is_open()) return false;
        file << root.dump(matjson::NO_INDENTATION);
        file.close();

        m_macroName = name;
        return true;
    } catch (...) {
        return false;
    }
}

bool BotManager::loadMacro(const std::string& name) {
    try {
        std::ifstream file(getMacroPath(name));
        if (!file.is_open()) return false;

        std::string content((std::istreambuf_iterator<char>(file)),
                             std::istreambuf_iterator<char>());
        file.close();

        auto parsed = matjson::parse(content);
        if (!parsed) return false;

        auto& root = parsed.value();
        if (!root.contains("inputs") || !root["inputs"].isArray()) return false;

        m_macro.clear();
        for (auto& entry : root["inputs"].asArray().value()) {
            InputFrame inp;
            inp.frame   = entry["f"].asInt().value_or(0);
            inp.player1 = entry["p1"].asBool().value_or(true);
            inp.hold    = entry["h"].asBool().value_or(true);
            inp.button  = entry["b"].asInt().value_or(1);
            m_macro.push_back(inp);
        }

        m_macroName = name;
        return true;
    } catch (...) {
        return false;
    }
}

bool BotManager::deleteMacro(const std::string& name) {
    try {
        auto path = getMacroPath(name);
        if (!std::filesystem::exists(path)) return false;
        std::filesystem::remove(path);
        if (m_macroName == name) m_macroName = "unnamed";
        return true;
    } catch (...) {
        return false;
    }
}

std::vector<std::string> BotManager::listMacros() const {
    std::vector<std::string> names;
    try {
        auto dir = getMacroDir();
        for (const auto& entry : std::filesystem::directory_iterator(dir)) {
            if (entry.path().extension() == ".xbot") {
                names.push_back(entry.path().stem().string());
            }
        }
    } catch (...) {}
    std::sort(names.begin(), names.end());
    return names;
}

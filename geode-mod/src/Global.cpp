#include "Global.hpp"
#include <fstream>
#include <matjson.hpp>

// File I/O is implemented via free functions here,
// keeping Global.hpp header-only except for this TU.

bool xdbSaveMacro(const std::string& name) {
    auto& g = Global::get();
    if (g.macro.empty()) return false;

    try {
        matjson::Value root = matjson::Value::object();
        root["version"] = 2;
        root["author"]  = Mod::get()->getDeveloper();
        root["tps"]     = (double)Global::getTPS();
        root["inputs"]  = matjson::Value::array();

        for (auto& inp : g.macro) {
            matjson::Value e = matjson::Value::object();
            e["f"]  = inp.frame;
            e["p2"] = inp.player2;
            e["h"]  = inp.hold;
            e["b"]  = inp.button;
            root["inputs"].push(std::move(e));
        }

        std::ofstream file(g.getMacroPath(name));
        if (!file.is_open()) return false;
        file << root.dump(matjson::NO_INDENTATION);
        g.macroName = name;
        return true;
    } catch (...) {
        return false;
    }
}

bool xdbLoadMacro(const std::string& name) {
    auto& g = Global::get();
    try {
        std::ifstream file(g.getMacroPath(name));
        if (!file.is_open()) return false;

        std::string content((std::istreambuf_iterator<char>(file)),
                             std::istreambuf_iterator<char>());
        auto parsed = matjson::parse(content);
        if (!parsed) return false;

        auto& root = parsed.value();
        if (!root.contains("inputs") || !root["inputs"].isArray()) return false;

        g.macro.clear();

        // Restore TPS if the macro was recorded with a specific rate
        if (root.contains("tps")) {
            float savedTPS = (float)root["tps"].asDouble().value_or(240.0);
            if (savedTPS != 240.f) {
                g.tpsEnabled = true;
                g.tps = savedTPS;
            }
        }

        for (auto& e : root["inputs"].asArray().value()) {
            InputFrame inp;
            inp.frame   = e["f"].asInt().value_or(0);
            inp.player2 = e["p2"].asBool().value_or(false);
            inp.hold    = e["h"].asBool().value_or(true);
            inp.button  = e["b"].asInt().value_or(1);
            g.macro.push_back(inp);
        }

        g.macroName = name;
        return true;
    } catch (...) {
        return false;
    }
}

bool xdbDeleteMacro(const std::string& name) {
    auto& g = Global::get();
    try {
        auto path = g.getMacroPath(name);
        if (!std::filesystem::exists(path)) return false;
        std::filesystem::remove(path);
        if (g.macroName == name) g.macroName = "unnamed";
        return true;
    } catch (...) {
        return false;
    }
}

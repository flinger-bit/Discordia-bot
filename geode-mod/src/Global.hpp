#pragma once
#include <Geode/Geode.hpp>
#include <vector>
#include <string>
#include <filesystem>

using namespace geode::prelude;

// ── Bot state ──────────────────────────────────────────────────────────────

enum class BotState { Disabled, Recording, Playing };

// ── Recorded input ─────────────────────────────────────────────────────────

struct InputFrame {
    int  frame;
    bool player2;   // matches xdbot convention: player2 flag
    bool hold;      // true = press, false = release
    int  button;    // 1 = jump, 2 = left, 3 = right
};

// ── Global singleton — all bot state lives here ───────────────────────────

class Global {
    Global() = default;

public:
    static Global& get() {
        static Global s;
        return s;
    }

    // ── Core state ───────────────────────────────────────────────────────
    BotState state = BotState::Disabled;

    std::vector<InputFrame> macro;
    std::string macroName = "unnamed";

    // Playback cursor
    size_t playbackIndex = 0;

    // ── TPS ──────────────────────────────────────────────────────────────
    bool  tpsEnabled = false;
    float tps        = 240.f;
    float leftOver   = 0.f;    // accumulator for TPS bypass fractional steps

    // ── Speedhack ────────────────────────────────────────────────────────
    bool  speedhackEnabled = false;
    float speedhackValue   = 1.f;

    // ── Noclip ───────────────────────────────────────────────────────────
    bool noclipEnabled  = false;
    bool noclipDead     = false;
    int  noclipDeaths   = 0;

    // ── Frame stepper ────────────────────────────────────────────────────
    bool frameStepperEnabled = false;
    bool stepFrame           = false;

    // ── Clickbot ─────────────────────────────────────────────────────────
    bool clickbotEnabled = false;

    // ── Frame offset (advanced accuracy setting) ──────────────────────────
    int frameOffset = 0;

    // ── Respawn ───────────────────────────────────────────────────────────
    bool respawnScheduled = false;

    // ── Helpers ──────────────────────────────────────────────────────────

    // Get the active TPS value
    static float getTPS() {
        auto& g = get();
        return g.tpsEnabled ? g.tps : 240.f;
    }

    // Frame number from m_gameState.m_levelTime — same method as real xdbot
    static int getCurrentFrame() {
        auto* pl = PlayLayer::get();
        if (!pl) return 0;

        auto& g = get();
        int frame = static_cast<int>(pl->m_gameState.m_levelTime * getTPS());
        frame++;  // 1-indexed, same as xdbot
        frame -= g.frameOffset;
        return frame < 0 ? 0 : frame;
    }

    std::string stateLabel() const {
        switch (state) {
            case BotState::Disabled:  return "OFF";
            case BotState::Recording: return "REC";
            case BotState::Playing:   return "PLAY";
        }
        return "?";
    }

    // ── Macro save dir ────────────────────────────────────────────────────
    std::filesystem::path getMacroDir() const {
        auto dir = Mod::get()->getSaveDir() / "macros";
        if (!std::filesystem::exists(dir))
            std::filesystem::create_directories(dir);
        return dir;
    }

    std::filesystem::path getMacroPath(const std::string& name) const {
        return getMacroDir() / (name + ".xbot");
    }

    std::vector<std::string> listMacros() const {
        std::vector<std::string> names;
        try {
            for (auto& e : std::filesystem::directory_iterator(getMacroDir())) {
                if (e.path().extension() == ".xbot")
                    names.push_back(e.path().stem().string());
            }
        } catch (...) {}
        std::sort(names.begin(), names.end());
        return names;
    }

    // ── Recording/playback helpers ────────────────────────────────────────
    void startRecording() {
        state = BotState::Recording;
        macro.clear();
        macroName = "unnamed";
    }

    void startPlaying() {
        state = BotState::Playing;
        playbackIndex = 0;
    }

    void stop() {
        state = BotState::Disabled;
        playbackIndex = 0;
        leftOver = 0.f;
    }

    void reset() {
        playbackIndex = 0;
        leftOver = 0.f;
        noclipDead = false;
        noclipDeaths = 0;
        respawnScheduled = false;
        stepFrame = false;
    }

    void recordInput(bool player2, bool hold, int button) {
        int frame = getCurrentFrame();
        // Deduplicate
        for (auto& i : macro) {
            if (i.frame == frame && i.player2 == player2 &&
                i.hold == hold && i.button == button) return;
        }
        macro.push_back({ frame, player2, hold, button });
    }

    // Get all inputs that should fire on a given frame
    std::vector<InputFrame> getInputsForFrame(int frame) const {
        std::vector<InputFrame> result;
        for (auto& i : macro) {
            if (i.frame == frame) result.push_back(i);
        }
        return result;
    }

    bool isPlaybackFinished() const {
        if (macro.empty()) return true;
        int lastFrame = 0;
        for (auto& i : macro) if (i.frame > lastFrame) lastFrame = i.frame;
        return getCurrentFrame() > lastFrame + 60;
    }
};

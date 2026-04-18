#pragma once
#include <Geode/Geode.hpp>
#include <vector>
#include <string>
#include <filesystem>

using namespace geode::prelude;

// ── Bot state ─────────────────────────────────────────────────────────────

enum class BotState {
    Disabled,
    Recording,
    Playing
};

// ── A single recorded input frame ─────────────────────────────────────────

struct InputFrame {
    int  frame;    // physics frame number
    bool player1;  // true = P1, false = P2
    bool hold;     // true = press, false = release
    int  button;   // 1 = jump, 2 = left, 3 = right
};

// ── Macro file format (JSON) ───────────────────────────────────────────────
// {
//   "version": 1,
//   "author": "flinger-bit",
//   "inputs": [ { "f": 42, "p1": true, "h": true, "b": 1 }, ... ]
// }

// ── BotManager singleton ──────────────────────────────────────────────────

class BotManager {
public:
    static BotManager* get();

    // ── State ────────────────────────────────────────────────────────────
    BotState getState() const { return m_state; }
    void     setState(BotState state);

    std::string stateLabel() const;

    // ── Frame counter ─────────────────────────────────────────────────────
    int  getCurrentFrame() const { return m_currentFrame; }
    void incrementFrame()        { ++m_currentFrame; }
    void resetFrame()            { m_currentFrame = 0; }

    // ── Macro data ────────────────────────────────────────────────────────
    const std::vector<InputFrame>& getMacro() const { return m_macro; }
    void clearMacro() { m_macro.clear(); }
    size_t getMacroSize() const { return m_macro.size(); }

    void setMacroName(const std::string& name) { m_macroName = name; }
    const std::string& getMacroName() const    { return m_macroName; }

    // ── Recording ─────────────────────────────────────────────────────────
    // Called from handleButton hook; deduplicates repeated frames.
    void recordInput(bool player1, bool hold, int button);

    // ── Playback ──────────────────────────────────────────────────────────
    // Returns inputs that should fire on the given frame for a player.
    std::vector<InputFrame> getInputsForFrame(int frame, bool player1) const;

    bool isPlaybackFinished() const;

    // ── Frame-step mode ───────────────────────────────────────────────────
    bool isFrameStep() const         { return m_frameStep; }
    void setFrameStep(bool v)        { m_frameStep = v; }
    bool shouldAdvanceFrame() const  { return m_advanceFrame; }
    void setAdvanceFrame(bool v)     { m_advanceFrame = v; }

    // ── Noclip ────────────────────────────────────────────────────────────
    void  setNoclipDead(bool v)   { m_noclipDead = v; }
    bool  isNoclipDead() const    { return m_noclipDead; }
    int   getNoclipDeaths() const { return m_noclipDeaths; }
    void  resetNoclipDeaths()     { m_noclipDeaths = 0; }

    // ── File I/O ──────────────────────────────────────────────────────────
    std::filesystem::path getMacroDir() const;
    std::filesystem::path getMacroPath(const std::string& name) const;

    bool saveMacro(const std::string& name);
    bool loadMacro(const std::string& name);
    bool deleteMacro(const std::string& name);

    std::vector<std::string> listMacros() const;

private:
    BotManager() = default;

    BotState              m_state        = BotState::Disabled;
    int                   m_currentFrame = 0;
    std::vector<InputFrame> m_macro;
    std::string           m_macroName    = "unnamed";

    bool m_frameStep    = false;
    bool m_advanceFrame = false;

    bool m_noclipDead   = false;
    int  m_noclipDeaths = 0;
};

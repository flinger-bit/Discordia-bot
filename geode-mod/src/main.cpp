#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PauseLayer.hpp>
#include <Geode/modify/PlayerObject.hpp>
#include <Geode/modify/CCScheduler.hpp>
#include "BotManager.hpp"
#include "layers/BotLayer.hpp"

using namespace geode::prelude;

// ═══════════════════════════════════════════════════════════════════════════
//  PLAY LAYER — frame counting, noclip, respawn, overlay, frame-step
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotPlayLayer, PlayLayer) {
    struct Fields {
        CCLabelBMFont* overlayLabel = nullptr;
        bool           respawnScheduled = false;
    };

    // ── init ────────────────────────────────────────────────────────────
    bool init(GJGameLevel* level, bool useReplay, bool dontCreateObjects) {
        if (!PlayLayer::init(level, useReplay, dontCreateObjects)) return false;

        auto* bot = BotManager::get();
        bot->resetFrame();
        bot->setNoclipDead(false);
        bot->resetNoclipDeaths();

        // Create overlay label
        if (Mod::get()->getSettingValue<bool>("show-overlay")) {
            auto* lbl = CCLabelBMFont::create("", "chatFont.fnt");
            lbl->setScale(0.55f);
            lbl->setAnchorPoint({0.f, 1.f});
            lbl->setZOrder(100);

            auto* winSize = CCDirector::get()->getWinSize();
            lbl->setPosition({4.f, winSize.height - 4.f});
            this->addChild(lbl);
            m_fields->overlayLabel = lbl;
        }

        return true;
    }

    // ── update ──────────────────────────────────────────────────────────
    void update(float dt) {
        auto* bot = BotManager::get();

        // Speedhack: scale dt before physics runs
        if (!m_isPracticeMode && Mod::get()->getSettingValue<bool>("speedhack")) {
            float speed = Mod::get()->getSettingValue<float>("speedhack-value");
            dt *= speed;
        }

        // Frame-step: only advance when explicitly requested
        if (bot->isFrameStep() && bot->getState() == BotState::Playing) {
            if (!bot->shouldAdvanceFrame()) return;
            bot->setAdvanceFrame(false);
        }

        PlayLayer::update(dt);
        bot->incrementFrame();

        // Overlay update
        if (m_fields->overlayLabel) {
            std::string txt =
                "XDBot | " + bot->stateLabel() +
                " | F:" + std::to_string(bot->getCurrentFrame()) +
                " | Macro:" + bot->getMacroName();

            if (Mod::get()->getSettingValue<bool>("noclip") && bot->getNoclipDeaths() > 0) {
                txt += " | NC:" + std::to_string(bot->getNoclipDeaths());
            }
            m_fields->overlayLabel->setString(txt.c_str());
        }
    }

    // ── onDeath ─────────────────────────────────────────────────────────
    void onDeath(GameObject* obj) {
        auto* bot = BotManager::get();

        // Noclip: skip death entirely
        if (Mod::get()->getSettingValue<bool>("noclip")) {
            bot->setNoclipDead(true);
            // increment private death counter for display
            // We use a workaround since we can't directly access noclipDeaths
            // through the field — BotManager tracks it
            return;
        }

        PlayLayer::onDeath(obj);

        // Auto-respawn
        if (!m_fields->respawnScheduled &&
            Mod::get()->getSettingValue<bool>("auto-respawn")) {
            m_fields->respawnScheduled = true;
            float delay = Mod::get()->getSettingValue<float>("auto-respawn-delay");
            this->scheduleOnce(schedule_selector(XDBotPlayLayer::doRespawn), delay);
        }
    }

    // ── resetLevel ──────────────────────────────────────────────────────
    void resetLevel() {
        PlayLayer::resetLevel();
        m_fields->respawnScheduled = false;

        auto* bot = BotManager::get();
        bot->resetFrame();
        bot->setNoclipDead(false);
    }

    // ── onQuit ──────────────────────────────────────────────────────────
    void onQuit() {
        PlayLayer::onQuit();
        auto* bot = BotManager::get();
        if (bot->getState() == BotState::Playing) {
            bot->setState(BotState::Disabled);
        }
        // Keep recording state so user can save after quitting
    }

    // ── schedule_selector callback ───────────────────────────────────────
    void doRespawn(float) {
        this->resetLevel();
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  GJBASE GAME LAYER — input recording & playback
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotGameLayer, GJBaseGameLayer) {
    // ── handleButton ─────────────────────────────────────────────────────
    // Signature: handleButton(bool hold, int button, bool player1)
    void handleButton(bool hold, int button, bool player1) {
        auto* bot = BotManager::get();

        switch (bot->getState()) {
            case BotState::Recording:
                // Record the input, then pass it through so the player can play
                bot->recordInput(player1, hold, button);
                GJBaseGameLayer::handleButton(hold, button, player1);
                break;

            case BotState::Playing:
                // Block all user input during playback — bot drives the inputs
                break;

            case BotState::Disabled:
            default:
                GJBaseGameLayer::handleButton(hold, button, player1);
                break;
        }
    }

    // ── update ───────────────────────────────────────────────────────────
    void update(float dt) {
        GJBaseGameLayer::update(dt);

        auto* bot = BotManager::get();
        if (bot->getState() != BotState::Playing) return;

        int frame = bot->getCurrentFrame();

        // Fire P1 inputs for this frame
        for (const auto& inp : bot->getInputsForFrame(frame, true)) {
            GJBaseGameLayer::handleButton(inp.hold, inp.button, true);
        }
        // Fire P2 inputs for this frame
        for (const auto& inp : bot->getInputsForFrame(frame, false)) {
            GJBaseGameLayer::handleButton(inp.hold, inp.button, false);
        }

        // Auto-stop when macro ends
        if (bot->isPlaybackFinished()) {
            bot->setState(BotState::Disabled);
            Notification::create("Playback finished.", NotificationIcon::Info)->show();
        }
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  PLAYER OBJECT — hitbox visualisation
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotPlayer, PlayerObject) {
    void update(float dt) {
        PlayerObject::update(dt);

        if (!Mod::get()->getSettingValue<bool>("show-hitboxes")) return;

        // Only show hitboxes in practice mode
        auto* pl = PlayLayer::get();
        if (!pl || !pl->m_isPracticeMode) return;

        // Draw hitbox outline using a simple debug box
        // The hitbox is m_objectRect; we create a CCDrawNode overlay if not present
        if (!this->getChildByTag(7777)) {
            auto* draw = CCDrawNode::create();
            draw->setTag(7777);
            draw->setZOrder(10);
            this->addChild(draw);
        }
        auto* draw = dynamic_cast<CCDrawNode*>(this->getChildByTag(7777));
        if (!draw) return;

        draw->clear();
        auto rect = m_objectRect;
        // Convert to local coordinates
        float hw = rect.size.width  / 2.f;
        float hh = rect.size.height / 2.f;
        ccColor4F outline = {1.f, 0.f, 0.f, 0.8f};
        draw->drawRect(
            {-hw, -hh}, {hw, hh},
            {0.f, 0.f, 0.f, 0.f},
            1.5f,
            outline
        );
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  PAUSE LAYER — add "Bot" button
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotPauseLayer, PauseLayer) {
    void customSetupButtons() {
        PauseLayer::customSetupButtons();

        auto* menu = this->getChildByType<CCMenu>(0);
        if (!menu) return;

        // Create Bot button
        auto* botSprite = ButtonSprite::create(
            "Bot", "goldFont.fnt", "GJ_button_01.png", 0.6f
        );
        botSprite->setScale(0.8f);

        auto* botBtn = CCMenuItemSpriteExtra::create(
            botSprite,
            this,
            menu_selector(XDBotPauseLayer::onBotLayer)
        );

        // Position near the bottom-left of the pause menu
        auto* winSize = CCDirector::get()->getWinSize();
        botBtn->setPosition({-winSize.width / 2.f + 50.f, -winSize.height / 2.f + 30.f});
        menu->addChild(botBtn);
    }

    void onBotLayer(CCObject*) {
        BotLayer::create()->show();
    }

    // Keyboard shortcut: B key in pause menu opens bot layer
    void keyDown(enumKeyCodes key) {
        if (key == enumKeyCodes::KEY_B) {
            BotLayer::create()->show();
            return;
        }
        PauseLayer::keyDown(key);
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  CCSCHEDULER — speedhack via time scale
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotScheduler, CCScheduler) {
    void update(float dt) {
        // Only apply speedhack if a level is active
        if (!PlayLayer::get()) {
            CCScheduler::update(dt);
            return;
        }
        if (Mod::get()->getSettingValue<bool>("speedhack")) {
            float speed = Mod::get()->getSettingValue<float>("speedhack-value");
            CCScheduler::update(dt * speed);
        } else {
            CCScheduler::update(dt);
        }
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  MOD ENTRY POINT
// ═══════════════════════════════════════════════════════════════════════════

$on_mod(Loaded) {
    log::info("XDBot Rework v{} loaded!", Mod::get()->getVersion().toString());
    log::info("Macro directory: {}", BotManager::get()->getMacroDir().string());
}

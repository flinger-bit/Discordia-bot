// ── XDBot Rework — Main Hooks ─────────────────────────────────────────────
// Handles:
//   • PlayLayer: init overlay + frame label, per-frame playback dispatch
//   • GJBaseGameLayer: handleButton recording/playback intercept
//   • PauseLayer: inject Bot button + keyboard shortcut

#include "includes.hpp"
#include "hacks/Clickbot.hpp"
#include "ui/BotLayer.hpp"

#include <Geode/modify/PlayLayer.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PauseLayer.hpp>

using namespace geode::prelude;

// ═══════════════════════════════════════════════════════════════════════════
//  PLAY LAYER
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotPlayLayer, PlayLayer) {
    struct Fields {
        CCLabelBMFont* overlayLabel = nullptr;
        CCLabelBMFont* frameLabel   = nullptr;
    };

    // ── init ────────────────────────────────────────────────────────────
    bool init(GJGameLevel* level, bool useReplay, bool dontCreateObjects) {
        if (!PlayLayer::init(level, useReplay, dontCreateObjects)) return false;

        auto& g = Global::get();
        g.reset();

        auto* ws = CCDirector::get()->getWinSize();

        // Overlay (top-left HUD)
        if (Mod::get()->getSettingValue<bool>("show-overlay")) {
            auto* lbl = CCLabelBMFont::create("", "chatFont.fnt");
            lbl->setScale(0.5f);
            lbl->setAnchorPoint({0.f, 1.f});
            lbl->setPosition({6.f, ws.height - 4.f});
            lbl->setZOrder(999);
            this->addChild(lbl);
            m_fields->overlayLabel = lbl;
        }

        // Frame label (top-right)
        if (Mod::get()->getSettingValue<bool>("frame-label")) {
            auto* lbl = CCLabelBMFont::create("Frame: 0", "chatFont.fnt");
            lbl->setScale(0.5f);
            lbl->setAnchorPoint({1.f, 1.f});
            lbl->setPosition({ws.width - 6.f, ws.height - 4.f});
            lbl->setZOrder(999);
            this->addChild(lbl);
            m_fields->frameLabel = lbl;
        }

        return true;
    }

    // ── postUpdate ──────────────────────────────────────────────────────
    void postUpdate(float dt) {
        PlayLayer::postUpdate(dt);

        auto& g = Global::get();
        int frame = Global::getCurrentFrame();

        // Update overlay
        if (m_fields->overlayLabel) {
            std::string txt = fmt::format(
                "XDBot | {} | F:{} | {}",
                g.stateLabel(), frame, g.macroName
            );
            if (Mod::get()->getSettingValue<bool>("noclip") && g.noclipDeaths > 0) {
                txt += fmt::format(" | NC:{}", g.noclipDeaths);
            }
            m_fields->overlayLabel->setString(txt.c_str());
        }

        // Update frame label
        if (m_fields->frameLabel) {
            m_fields->frameLabel->setString(
                ("Frame: " + std::to_string(frame)).c_str()
            );
        }

        // ── Playback: fire inputs for this frame ──────────────────────
        // handleButton(hold, button, player1)
        // inp.player2 flag: false = player1, true = player2
        if (g.state == BotState::Playing) {
            for (auto& inp : g.getInputsForFrame(frame)) {
                GJBaseGameLayer::handleButton(inp.hold, inp.button, !inp.player2);
            }

            // Check if done
            if (Mod::get()->getSettingValue<bool>("auto-stop-playing") &&
                g.isPlaybackFinished()) {
                g.state = BotState::Disabled;
                Notification::create("Playback finished!", NotificationIcon::Info)->show();
            }
        }
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  GJBASE GAME LAYER — input intercept
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotGameLayer, GJBaseGameLayer) {

    // handleButton(hold, button, player1)
    void handleButton(bool hold, int button, bool player1) {
        auto& g = Global::get();

        switch (g.state) {

            case BotState::Recording:
                // Record the input then pass through (player can still play)
                g.recordInput(!player1, hold, button);  // player2 = !player1
                Clickbot::onInput(hold, button, !player1);
                GJBaseGameLayer::handleButton(hold, button, player1);
                break;

            case BotState::Playing:
                // Block all manual input during playback
                // (inputs are fired from postUpdate instead)
                Clickbot::onInput(hold, button, !player1);
                break;

            case BotState::Disabled:
            default:
                Clickbot::onInput(hold, button, !player1);
                GJBaseGameLayer::handleButton(hold, button, player1);
                break;
        }
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  PAUSE LAYER — Bot button + B shortcut
// ═══════════════════════════════════════════════════════════════════════════

class $modify(XDBotPauseLayer, PauseLayer) {

    void customSetupButtons() {
        PauseLayer::customSetupButtons();

        auto* menu = this->getChildByType<CCMenu>(0);
        if (!menu) return;

        auto* lbl = ButtonSprite::create(
            "Bot", "goldFont.fnt", "GJ_button_01.png", 0.6f
        );
        lbl->setScale(0.75f);

        auto* btn = CCMenuItemSpriteExtra::create(
            lbl, this, menu_selector(XDBotPauseLayer::onBotLayer)
        );

        auto* ws = CCDirector::get()->getWinSize();
        btn->setPosition({-ws.width * 0.5f + 50.f, -ws.height * 0.5f + 28.f});
        menu->addChild(btn);
    }

    void onBotLayer(CCObject*) {
        BotLayer::create()->show();
    }

    void keyDown(enumKeyCodes key) {
        if (key == enumKeyCodes::KEY_B) {
            BotLayer::create()->show();
            return;
        }
        PauseLayer::keyDown(key);
    }
};

// ═══════════════════════════════════════════════════════════════════════════
//  MOD ENTRY
// ═══════════════════════════════════════════════════════════════════════════

$on_mod(Loaded) {
    log::info("XDBot Rework v{} loaded!", Mod::get()->getVersion().toString());
}

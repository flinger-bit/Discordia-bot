#pragma once
#include <Geode/Geode.hpp>
#include "../BotManager.hpp"

using namespace geode::prelude;

class BotLayer : public Popup<> {
public:
    static BotLayer* create();

    static const float WIDTH;
    static const float HEIGHT;

protected:
    bool setup() override;
    void onClose(CCObject*) override;

private:
    // State buttons
    CCMenuItemSpriteExtra* m_btnDisabled  = nullptr;
    CCMenuItemSpriteExtra* m_btnRecord    = nullptr;
    CCMenuItemSpriteExtra* m_btnPlay      = nullptr;

    // Info labels
    CCLabelBMFont* m_macroNameLabel  = nullptr;
    CCLabelBMFont* m_macroFrameLabel = nullptr;
    CCLabelBMFont* m_stateLabel      = nullptr;

    // Callbacks
    void onDisabled(CCObject*);
    void onRecord(CCObject*);
    void onPlay(CCObject*);
    void onSave(CCObject*);
    void onLoad(CCObject*);
    void onClear(CCObject*);
    void onNoclip(CCObject*);
    void onAutoRespawn(CCObject*);
    void onSpeedhack(CCObject*);

    void updateStateUI();
    void refreshInfo();

    // Utility: create a coloured rounded button with text
    CCMenuItemSpriteExtra* makeTextButton(
        const std::string& text,
        SEL_MenuHandler sel,
        const ccColor3B& col,
        float width,
        float height
    );

    void update(float dt) override;
};

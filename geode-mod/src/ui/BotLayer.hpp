#pragma once
#include <Geode/Geode.hpp>
#include "../Global.hpp"
#include "../Macro.hpp"

using namespace geode::prelude;

class BotLayer : public Popup<> {
public:
    static BotLayer* create();

    static constexpr float W = 330.f;
    static constexpr float H = 300.f;

protected:
    bool  setup() override;
    void  onClose(CCObject*) override;
    void  update(float dt) override;

private:
    // State row
    CCMenuItemSpriteExtra* m_btnOff    = nullptr;
    CCMenuItemSpriteExtra* m_btnRecord = nullptr;
    CCMenuItemSpriteExtra* m_btnPlay   = nullptr;

    // Info labels
    CCLabelBMFont* m_stateLabel  = nullptr;
    CCLabelBMFont* m_infoLabel   = nullptr;

    // Toggle labels (show ON/OFF per toggle)
    CCLabelBMFont* m_noclipLbl    = nullptr;
    CCLabelBMFont* m_respawnLbl   = nullptr;
    CCLabelBMFont* m_speedLbl     = nullptr;
    CCLabelBMFont* m_tpsLbl       = nullptr;
    CCLabelBMFont* m_clickbotLbl  = nullptr;
    CCLabelBMFont* m_stepperLbl   = nullptr;

    void onOff(CCObject*);
    void onRecord(CCObject*);
    void onPlay(CCObject*);
    void onSave(CCObject*);
    void onLoad(CCObject*);
    void onClear(CCObject*);
    void onToggleNoclip(CCObject*);
    void onToggleRespawn(CCObject*);
    void onToggleSpeedhack(CCObject*);
    void onToggleTPS(CCObject*);
    void onToggleClickbot(CCObject*);
    void onToggleStepper(CCObject*);

    void refreshState();
    void refreshInfo();
    void refreshToggles();

    static CCMenuItemSpriteExtra* makeBtn(
        const std::string& text,
        SEL_MenuHandler sel,
        CCObject* target,
        const ccColor3B& color,
        float w, float h
    );
    static CCLabelBMFont* statusLbl(const char* initial);
};

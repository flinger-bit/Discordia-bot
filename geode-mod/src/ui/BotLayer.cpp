#include "BotLayer.hpp"
#include "MacroListLayer.hpp"

using namespace geode::prelude;

// ── Factory ───────────────────────────────────────────────────────────────

BotLayer* BotLayer::create() {
    auto* ret = new BotLayer();
    if (ret && ret->initAnchored(W, H, "GJ_square05.png", "square", {0,0,94,94})) {
        ret->autorelease();
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

// ── Setup ─────────────────────────────────────────────────────────────────

bool BotLayer::setup() {
    this->setTitle("XDBot Rework");
    this->scheduleUpdate();

    // ── State label ───────────────────────────────────────────────────────
    m_stateLabel = CCLabelBMFont::create("OFF", "goldFont.fnt");
    m_stateLabel->setScale(0.55f);
    m_stateLabel->setPosition({W / 2.f, H - 42.f});
    m_mainLayer->addChild(m_stateLabel);

    // ── State buttons (OFF / RECORD / PLAY) ───────────────────────────────
    auto* stateMenu = CCMenu::create();
    stateMenu->setPosition({0, 0});
    m_mainLayer->addChild(stateMenu);

    const float btnW = 78.f, btnH = 26.f, gap = 8.f;
    const float rowY  = H - 68.f;
    const float totalW = 3.f * btnW + 2.f * gap;
    const float sx = (W - totalW) / 2.f + btnW / 2.f;

    m_btnOff    = makeBtn("OFF",    menu_selector(BotLayer::onOff),    this, {80,80,80},   btnW, btnH);
    m_btnRecord = makeBtn("Record", menu_selector(BotLayer::onRecord), this, {160,30,30},  btnW, btnH);
    m_btnPlay   = makeBtn("Play",   menu_selector(BotLayer::onPlay),   this, {30,130,30},  btnW, btnH);

    m_btnOff->setPosition({sx,                  rowY});
    m_btnRecord->setPosition({sx + btnW + gap,   rowY});
    m_btnPlay->setPosition({sx + 2*(btnW + gap), rowY});

    stateMenu->addChild(m_btnOff);
    stateMenu->addChild(m_btnRecord);
    stateMenu->addChild(m_btnPlay);

    // ── Info label ────────────────────────────────────────────────────────
    m_infoLabel = CCLabelBMFont::create("", "chatFont.fnt");
    m_infoLabel->setScale(0.48f);
    m_infoLabel->setPosition({W / 2.f, rowY - 26.f});
    m_infoLabel->setColor({180, 180, 180});
    m_mainLayer->addChild(m_infoLabel);

    // ── Action buttons (Save / Load / Clear) ──────────────────────────────
    auto* actionMenu = CCMenu::create();
    actionMenu->setPosition({0, 0});
    m_mainLayer->addChild(actionMenu);

    const float abtnW = 66.f, abtnH = 22.f, agap = 6.f;
    const float arowY = rowY - 52.f;
    const float atotalW = 3.f * abtnW + 2.f * agap;
    const float asx = (W - atotalW) / 2.f + abtnW / 2.f;

    auto* saveBtn  = makeBtn("Save",  menu_selector(BotLayer::onSave),  this, {30,80,160},  abtnW, abtnH);
    auto* loadBtn  = makeBtn("Load",  menu_selector(BotLayer::onLoad),  this, {100,65,20},  abtnW, abtnH);
    auto* clearBtn = makeBtn("Clear", menu_selector(BotLayer::onClear), this, {120,20,20},  abtnW, abtnH);

    saveBtn->setPosition( {asx,                   arowY});
    loadBtn->setPosition( {asx + abtnW + agap,    arowY});
    clearBtn->setPosition({asx + 2*(abtnW + agap),arowY});

    actionMenu->addChild(saveBtn);
    actionMenu->addChild(loadBtn);
    actionMenu->addChild(clearBtn);

    // ── Separator ─────────────────────────────────────────────────────────
    auto* sep = CCLayerColor::create({255,255,255,30}, W - 30.f, 1.f);
    sep->setPosition({15.f, arowY - 18.f});
    m_mainLayer->addChild(sep);

    // ── Toggle grid (2 columns) ───────────────────────────────────────────
    auto* toggleMenu = CCMenu::create();
    toggleMenu->setPosition({0, 0});
    m_mainLayer->addChild(toggleMenu);

    struct ToggleDef {
        const char*      label;
        SEL_MenuHandler  cb;
        CCLabelBMFont**  lblPtr;
        ccColor3B        color;
    };

    const float tW = 120.f, tH = 22.f, tgapX = 12.f, tgapY = 8.f;
    float tBaseY = arowY - 44.f;

    ToggleDef defs[] = {
        { "Noclip",    menu_selector(BotLayer::onToggleNoclip),    &m_noclipLbl,   {60,60,140} },
        { "AutoRespawn",menu_selector(BotLayer::onToggleRespawn),  &m_respawnLbl,  {60,110,60} },
        { "Speedhack", menu_selector(BotLayer::onToggleSpeedhack), &m_speedLbl,    {100,70,20} },
        { "TPS Bypass",menu_selector(BotLayer::onToggleTPS),       &m_tpsLbl,      {20,90,110} },
        { "Clickbot",  menu_selector(BotLayer::onToggleClickbot),  &m_clickbotLbl, {110,30,90} },
        { "FrameStep", menu_selector(BotLayer::onToggleStepper),   &m_stepperLbl,  {50,50,50}  },
    };

    for (int i = 0; i < 6; ++i) {
        int  col = i % 2;
        int  row = i / 2;
        float x  = (W / 2.f) - tW / 2.f - tgapX / 2.f + col * (tW + tgapX);
        float y  = tBaseY - row * (tH + tgapY);

        auto* bg = CCScale9Sprite::create("square02_small.png", {0,0,40,40});
        bg->setContentSize({tW, tH});
        bg->setColor(defs[i].color);
        bg->setOpacity(160);

        // Toggle label text
        auto* lbl = CCLabelBMFont::create(defs[i].label, "chatFont.fnt");
        lbl->setScale(0.42f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({6.f, tH / 2.f});
        bg->addChild(lbl);

        // ON/OFF indicator
        auto* statusLblObj = CCLabelBMFont::create("OFF", "chatFont.fnt");
        statusLblObj->setScale(0.38f);
        statusLblObj->setAnchorPoint({1.f, 0.5f});
        statusLblObj->setPosition({tW - 5.f, tH / 2.f});
        statusLblObj->setColor({140,140,140});
        bg->addChild(statusLblObj);
        *defs[i].lblPtr = statusLblObj;

        auto* btn = CCMenuItemSpriteExtra::create(bg, this, defs[i].cb);
        btn->setPosition({x, y});
        toggleMenu->addChild(btn);
    }

    refreshState();
    refreshInfo();
    refreshToggles();
    return true;
}

// ── Update ────────────────────────────────────────────────────────────────

void BotLayer::update(float dt) {
    Popup<>::update(dt);
    refreshInfo();
}

// ── State callbacks ───────────────────────────────────────────────────────

void BotLayer::onOff(CCObject*) {
    Global::get().stop();
    refreshState();
}

void BotLayer::onRecord(CCObject*) {
    Global::get().startRecording();
    refreshState();
    Notification::create("Recording — play the level!", NotificationIcon::Info)->show();
}

void BotLayer::onPlay(CCObject*) {
    auto& g = Global::get();
    if (g.macro.empty()) {
        FLAlertLayer::create("No Macro", "Record or load a macro first.", "OK")->show();
        return;
    }
    g.startPlaying();
    refreshState();
    Notification::create("Playback started!", NotificationIcon::Success)->show();
}

// ── Action callbacks ──────────────────────────────────────────────────────

void BotLayer::onSave(CCObject*) {
    auto& g = Global::get();
    if (g.macro.empty()) {
        FLAlertLayer::create("Empty", "Nothing recorded to save.", "OK")->show();
        return;
    }
    if (xdbSaveMacro(g.macroName)) {
        Notification::create("Saved: " + g.macroName, NotificationIcon::Success)->show();
    } else {
        FLAlertLayer::create("Error", "Failed to save macro.", "OK")->show();
    }
    refreshInfo();
}

void BotLayer::onLoad(CCObject*) {
    MacroListLayer::create(this)->show();
}

void BotLayer::onClear(CCObject*) {
    createQuickPopup(
        "Clear Macro",
        "Are you sure you want to clear the current macro?",
        "Cancel", "Clear",
        [this](auto*, bool confirmed) {
            if (!confirmed) return;
            auto& g = Global::get();
            g.macro.clear();
            g.macroName = "unnamed";
            Notification::create("Macro cleared.", NotificationIcon::Info)->show();
            refreshInfo();
        }
    );
}

// ── Toggle callbacks ──────────────────────────────────────────────────────

void BotLayer::onToggleNoclip(CCObject*) {
    auto* mod = Mod::get();
    bool v = !mod->getSettingValue<bool>("noclip");
    mod->setSettingValue("noclip", v);
    Global::get().noclipEnabled = v;
    refreshToggles();
}

void BotLayer::onToggleRespawn(CCObject*) {
    auto* mod = Mod::get();
    bool v = !mod->getSettingValue<bool>("auto-respawn");
    mod->setSettingValue("auto-respawn", v);
    refreshToggles();
}

void BotLayer::onToggleSpeedhack(CCObject*) {
    auto& g = Global::get();
    bool v = !Mod::get()->getSettingValue<bool>("speedhack");
    Mod::get()->setSettingValue("speedhack", v);
    g.speedhackEnabled = v;
    refreshToggles();
}

void BotLayer::onToggleTPS(CCObject*) {
    auto& g = Global::get();
    bool v = !Mod::get()->getSettingValue<bool>("tps-bypass");
    Mod::get()->setSettingValue("tps-bypass", v);
    g.tpsEnabled = v;
    refreshToggles();
}

void BotLayer::onToggleClickbot(CCObject*) {
    auto& g = Global::get();
    bool v = !Mod::get()->getSettingValue<bool>("clickbot");
    Mod::get()->setSettingValue("clickbot", v);
    g.clickbotEnabled = v;
    refreshToggles();
}

void BotLayer::onToggleStepper(CCObject*) {
    auto& g = Global::get();
    bool v = !Mod::get()->getSettingValue<bool>("frame-stepper");
    Mod::get()->setSettingValue("frame-stepper", v);
    g.frameStepperEnabled = v;
    refreshToggles();
}

// ── UI refresh ────────────────────────────────────────────────────────────

void BotLayer::refreshState() {
    auto& g = Global::get();

    auto dim  = [](CCMenuItemSpriteExtra* btn) {
        if (btn->getChildrenCount()) {
            if (auto* bg = dynamic_cast<CCScale9Sprite*>(btn->getChildren()->objectAtIndex(0)))
                bg->setOpacity(90);
        }
    };
    auto hi = [](CCMenuItemSpriteExtra* btn) {
        if (btn->getChildrenCount()) {
            if (auto* bg = dynamic_cast<CCScale9Sprite*>(btn->getChildren()->objectAtIndex(0)))
                bg->setOpacity(255);
        }
    };

    dim(m_btnOff); dim(m_btnRecord); dim(m_btnPlay);

    switch (g.state) {
        case BotState::Disabled:
            hi(m_btnOff);
            m_stateLabel->setString("OFF");
            m_stateLabel->setColor({140,140,140});
            break;
        case BotState::Recording:
            hi(m_btnRecord);
            m_stateLabel->setString("● RECORDING");
            m_stateLabel->setColor({255,80,80});
            break;
        case BotState::Playing:
            hi(m_btnPlay);
            m_stateLabel->setString("▶ PLAYING");
            m_stateLabel->setColor({80,220,80});
            break;
    }
}

void BotLayer::refreshInfo() {
    auto& g = Global::get();
    int frame = Global::getCurrentFrame();
    m_infoLabel->setString(fmt::format(
        "Macro: {}  |  Inputs: {}  |  Frame: {}",
        g.macroName, g.macro.size(), frame
    ).c_str());
}

void BotLayer::refreshToggles() {
    auto* mod = Mod::get();
    auto setLbl = [](CCLabelBMFont* lbl, bool on) {
        lbl->setString(on ? "ON" : "OFF");
        lbl->setColor(on ? ccColor3B{80,220,80} : ccColor3B{140,140,140});
    };

    setLbl(m_noclipLbl,   mod->getSettingValue<bool>("noclip"));
    setLbl(m_respawnLbl,  mod->getSettingValue<bool>("auto-respawn"));
    setLbl(m_speedLbl,    mod->getSettingValue<bool>("speedhack"));
    setLbl(m_tpsLbl,      mod->getSettingValue<bool>("tps-bypass"));
    setLbl(m_clickbotLbl, mod->getSettingValue<bool>("clickbot"));
    setLbl(m_stepperLbl,  mod->getSettingValue<bool>("frame-stepper"));
}

void BotLayer::onClose(CCObject* sender) {
    Popup<>::onClose(sender);
}

// ── Static helpers ────────────────────────────────────────────────────────

CCMenuItemSpriteExtra* BotLayer::makeBtn(
    const std::string& text,
    SEL_MenuHandler sel,
    CCObject* target,
    const ccColor3B& color,
    float w, float h
) {
    auto* bg = CCScale9Sprite::create("square02_small.png", {0,0,40,40});
    bg->setContentSize({w, h});
    bg->setColor(color);
    bg->setOpacity(200);

    auto* lbl = CCLabelBMFont::create(text.c_str(), "chatFont.fnt");
    float maxScale = (w - 10.f) / lbl->getContentSize().width;
    lbl->setScale(std::min(0.5f, maxScale));
    lbl->setPosition({w / 2.f, h / 2.f});
    bg->addChild(lbl);

    return CCMenuItemSpriteExtra::create(bg, target, sel);
}

CCLabelBMFont* BotLayer::statusLbl(const char* initial) {
    auto* lbl = CCLabelBMFont::create(initial, "chatFont.fnt");
    lbl->setScale(0.38f);
    lbl->setColor({140,140,140});
    return lbl;
}

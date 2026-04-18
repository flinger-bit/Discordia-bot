#include "BotLayer.hpp"
#include "MacroListLayer.hpp"
#include <Geode/ui/TextInput.hpp>

using namespace geode::prelude;

const float BotLayer::WIDTH  = 320.f;
const float BotLayer::HEIGHT = 260.f;

// ── Factory ───────────────────────────────────────────────────────────────

BotLayer* BotLayer::create() {
    auto* ret = new BotLayer();
    if (ret && ret->initAnchored(WIDTH, HEIGHT, "GJ_square05.png", "square", {0,0,94,94})) {
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

    auto* bot = BotManager::get();
    auto* winSize = CCDirector::get()->getWinSize();

    // ── Background of content area ───────────────────────────────────────
    auto* bg = CCScale9Sprite::create("square02_small.png", {0,0,40,40});
    bg->setContentSize({WIDTH - 20.f, HEIGHT - 60.f});
    bg->setPosition({WIDTH / 2.f, HEIGHT / 2.f - 10.f});
    bg->setColor({0, 0, 0});
    bg->setOpacity(80);
    m_mainLayer->addChild(bg, 0);

    // ── State label ───────────────────────────────────────────────────────
    m_stateLabel = CCLabelBMFont::create("OFF", "goldFont.fnt");
    m_stateLabel->setScale(0.6f);
    m_stateLabel->setPosition({WIDTH / 2.f, HEIGHT - 42.f});
    m_stateLabel->setColor({200, 200, 200});
    m_mainLayer->addChild(m_stateLabel);

    // ── State buttons row ─────────────────────────────────────────────────
    float btnW = 76.f, btnH = 28.f, gap = 8.f;
    float rowY = HEIGHT - 68.f;
    float totalW = 3 * btnW + 2 * gap;
    float startX = (WIDTH - totalW) / 2.f + btnW / 2.f;

    auto* stateMenu = CCMenu::create();
    stateMenu->setPosition({0, 0});
    m_mainLayer->addChild(stateMenu);

    m_btnDisabled = makeTextButton("Disabled", menu_selector(BotLayer::onDisabled),
                                  {120,120,120}, btnW, btnH);
    m_btnDisabled->setPosition({startX, rowY});
    stateMenu->addChild(m_btnDisabled);

    m_btnRecord = makeTextButton("Record", menu_selector(BotLayer::onRecord),
                                 {180,30,30}, btnW, btnH);
    m_btnRecord->setPosition({startX + btnW + gap, rowY});
    stateMenu->addChild(m_btnRecord);

    m_btnPlay = makeTextButton("Play", menu_selector(BotLayer::onPlay),
                               {30,130,30}, btnW, btnH);
    m_btnPlay->setPosition({startX + 2*(btnW + gap), rowY});
    stateMenu->addChild(m_btnPlay);

    // ── Macro info ────────────────────────────────────────────────────────
    m_macroNameLabel = CCLabelBMFont::create("Macro: unnamed", "chatFont.fnt");
    m_macroNameLabel->setScale(0.55f);
    m_macroNameLabel->setPosition({WIDTH / 2.f, rowY - 32.f});
    m_macroNameLabel->setColor({220,220,220});
    m_mainLayer->addChild(m_macroNameLabel);

    m_macroFrameLabel = CCLabelBMFont::create("Frames: 0", "chatFont.fnt");
    m_macroFrameLabel->setScale(0.48f);
    m_macroFrameLabel->setPosition({WIDTH / 2.f, rowY - 48.f});
    m_macroFrameLabel->setColor({160,160,160});
    m_mainLayer->addChild(m_macroFrameLabel);

    // ── Action buttons row ────────────────────────────────────────────────
    auto* actionMenu = CCMenu::create();
    actionMenu->setPosition({0, 0});
    m_mainLayer->addChild(actionMenu);

    float abtnW = 62.f, abtnH = 24.f, agap = 6.f;
    float arowY = rowY - 76.f;
    float atotalW = 4 * abtnW + 3 * agap;
    float astartX = (WIDTH - atotalW) / 2.f + abtnW / 2.f;

    auto* saveBtn = makeTextButton("Save", menu_selector(BotLayer::onSave),
                                   {30,80,180}, abtnW, abtnH);
    saveBtn->setPosition({astartX, arowY});
    actionMenu->addChild(saveBtn);

    auto* loadBtn = makeTextButton("Load", menu_selector(BotLayer::onLoad),
                                   {100,60,20}, abtnW, abtnH);
    loadBtn->setPosition({astartX + abtnW + agap, arowY});
    actionMenu->addChild(loadBtn);

    auto* clearBtn = makeTextButton("Clear", menu_selector(BotLayer::onClear),
                                    {120,20,20}, abtnW, abtnH);
    clearBtn->setPosition({astartX + 2*(abtnW + agap), arowY});
    actionMenu->addChild(clearBtn);

    // ── Toggle row ────────────────────────────────────────────────────────
    auto* toggleMenu = CCMenu::create();
    toggleMenu->setPosition({0, 0});
    m_mainLayer->addChild(toggleMenu);

    float tY = arowY - 32.f;
    float tbtnW = 92.f, tbtnH = 22.f, tgap = 6.f;
    float ttotalW = 3 * tbtnW + 2 * tgap;
    float tstartX = (WIDTH - ttotalW) / 2.f + tbtnW / 2.f;

    auto* noclipBtn = makeTextButton("Noclip", menu_selector(BotLayer::onNoclip),
                                     {60,60,120}, tbtnW, tbtnH);
    noclipBtn->setPosition({tstartX, tY});
    toggleMenu->addChild(noclipBtn);

    auto* respawnBtn = makeTextButton("AutoRespawn", menu_selector(BotLayer::onAutoRespawn),
                                      {60,100,60}, tbtnW, tbtnH);
    respawnBtn->setPosition({tstartX + tbtnW + tgap, tY});
    toggleMenu->addChild(respawnBtn);

    auto* speedBtn = makeTextButton("Speedhack", menu_selector(BotLayer::onSpeedhack),
                                    {100,80,20}, tbtnW, tbtnH);
    speedBtn->setPosition({tstartX + 2*(tbtnW + tgap), tY});
    toggleMenu->addChild(speedBtn);

    updateStateUI();
    refreshInfo();

    return true;
}

// ── State callbacks ───────────────────────────────────────────────────────

void BotLayer::onDisabled(CCObject*) {
    BotManager::get()->setState(BotState::Disabled);
    updateStateUI();
}

void BotLayer::onRecord(CCObject*) {
    BotManager::get()->setState(BotState::Recording);
    updateStateUI();
    Notification::create("Recording started — play the level!", NotificationIcon::Info)->show();
}

void BotLayer::onPlay(CCObject*) {
    if (BotManager::get()->getMacroSize() == 0) {
        FLAlertLayer::create("No Macro", "Record or load a macro first.", "OK")->show();
        return;
    }
    BotManager::get()->setState(BotState::Playing);
    updateStateUI();
    Notification::create("Playback started!", NotificationIcon::Success)->show();
}

// ── Action callbacks ──────────────────────────────────────────────────────

void BotLayer::onSave(CCObject*) {
    auto* bot = BotManager::get();
    if (bot->getMacroSize() == 0) {
        FLAlertLayer::create("Empty Macro", "Nothing recorded to save.", "OK")->show();
        return;
    }

    // Input dialog for macro name
    auto* alert = FLAlertLayer::create(
        nullptr,
        "Save Macro",
        "Enter a name for this macro.",
        "Cancel", "Save"
    );
    // Use a simple prompt approach — inline text input via Geode TextInput in a popup
    // For simplicity use the current macro name
    std::string name = bot->getMacroName();
    if (bot->saveMacro(name)) {
        Notification::create("Macro saved: " + name, NotificationIcon::Success)->show();
    } else {
        FLAlertLayer::create("Error", "Failed to save macro.", "OK")->show();
    }
    refreshInfo();
}

void BotLayer::onLoad(CCObject*) {
    MacroListLayer::create(this)->show();
}

void BotLayer::onClear(CCObject*) {
    auto* alert = FLAlertLayer::create(
        this,
        "Clear Macro",
        "Are you sure you want to clear the current macro?",
        "Cancel", "Clear"
    );
    alert->m_button2->addClickEventListener([this](CCObject*) {
        BotManager::get()->clearMacro();
        Notification::create("Macro cleared.", NotificationIcon::Info)->show();
        refreshInfo();
    });
    alert->show();
}

// ── Toggle callbacks ──────────────────────────────────────────────────────

void BotLayer::onNoclip(CCObject*) {
    auto* mod = Mod::get();
    bool cur = mod->getSettingValue<bool>("noclip");
    auto result = mod->setSettingValue("noclip", !cur);
    Notification::create(
        std::string("Noclip ") + (!cur ? "ON" : "OFF"),
        !cur ? NotificationIcon::Success : NotificationIcon::Info
    )->show();
}

void BotLayer::onAutoRespawn(CCObject*) {
    auto* mod = Mod::get();
    bool cur = mod->getSettingValue<bool>("auto-respawn");
    mod->setSettingValue("auto-respawn", !cur);
    Notification::create(
        std::string("Auto Respawn ") + (!cur ? "ON" : "OFF"),
        !cur ? NotificationIcon::Success : NotificationIcon::Info
    )->show();
}

void BotLayer::onSpeedhack(CCObject*) {
    auto* mod = Mod::get();
    bool cur = mod->getSettingValue<bool>("speedhack");
    mod->setSettingValue("speedhack", !cur);
    Notification::create(
        std::string("Speedhack ") + (!cur ? "ON" : "OFF"),
        !cur ? NotificationIcon::Success : NotificationIcon::Info
    )->show();
}

// ── UI helpers ────────────────────────────────────────────────────────────

void BotLayer::updateStateUI() {
    auto* bot = BotManager::get();

    // Reset all button colours
    auto dimColor = [](CCMenuItemSpriteExtra* btn) {
        if (auto* bg = dynamic_cast<CCScale9Sprite*>(btn->getChildren()->objectAtIndex(0))) {
            bg->setOpacity(100);
        }
    };
    auto highlightColor = [](CCMenuItemSpriteExtra* btn) {
        if (auto* bg = dynamic_cast<CCScale9Sprite*>(btn->getChildren()->objectAtIndex(0))) {
            bg->setOpacity(255);
        }
    };

    dimColor(m_btnDisabled);
    dimColor(m_btnRecord);
    dimColor(m_btnPlay);

    switch (bot->getState()) {
        case BotState::Disabled:
            highlightColor(m_btnDisabled);
            m_stateLabel->setString("DISABLED");
            m_stateLabel->setColor({160,160,160});
            break;
        case BotState::Recording:
            highlightColor(m_btnRecord);
            m_stateLabel->setString("● RECORDING");
            m_stateLabel->setColor({255,80,80});
            break;
        case BotState::Playing:
            highlightColor(m_btnPlay);
            m_stateLabel->setString("▶ PLAYING");
            m_stateLabel->setColor({80,255,80});
            break;
    }
}

void BotLayer::refreshInfo() {
    auto* bot = BotManager::get();
    m_macroNameLabel->setString(("Macro: " + bot->getMacroName()).c_str());
    m_macroFrameLabel->setString(("Inputs: " + std::to_string(bot->getMacroSize()) +
                                   "  |  Frames: " + std::to_string(bot->getCurrentFrame())).c_str());
}

void BotLayer::update(float dt) {
    Popup<>::update(dt);
    refreshInfo();
}

void BotLayer::onClose(CCObject* sender) {
    Popup<>::onClose(sender);
}

// ── Button factory ────────────────────────────────────────────────────────

CCMenuItemSpriteExtra* BotLayer::makeTextButton(
    const std::string& text,
    SEL_MenuHandler sel,
    const ccColor3B& col,
    float width,
    float height
) {
    auto* bg = CCScale9Sprite::create("square02_small.png", {0,0,40,40});
    bg->setContentSize({width, height});
    bg->setColor(col);
    bg->setOpacity(200);

    auto* label = CCLabelBMFont::create(text.c_str(), "chatFont.fnt");
    label->setScale(std::min(0.5f, (width - 10.f) / (label->getContentSize().width * 0.5f)));
    label->setPosition({width / 2.f, height / 2.f});
    bg->addChild(label);

    auto* btn = CCMenuItemSpriteExtra::create(bg, this, sel);
    return btn;
}

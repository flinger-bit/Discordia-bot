#pragma once
#include "../Global.hpp"

// Plays click sounds on button press/release.
// Sound files live in resources/hold_click.mp3 and release_click.mp3.

class Clickbot {
public:
    // Called from the handleButton hook
    static void onInput(bool hold, int button, bool player2);

private:
    static void playSound(const std::string& filename, float volume);
};

package dev.albedo.modern;

//? if >=1.17 {
import com.mojang.blaze3d.platform.InputConstants;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}

/** The codes of the keys, the mouse button and the key actions that the screens and the harness read, from the class that has them in this version. */
final class KeyCodes {
    private KeyCodes() {}

    //? if >=1.17 {
    static final int KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
    static final int KEY_DELETE = InputConstants.KEY_DELETE;
    static final int KEY_LEFT = InputConstants.KEY_LEFT;
    static final int KEY_RIGHT = InputConstants.KEY_RIGHT;
    static final int KEY_UP = InputConstants.KEY_UP;
    static final int KEY_DOWN = InputConstants.KEY_DOWN;
    static final int KEY_HOME = InputConstants.KEY_HOME;
    static final int KEY_END = InputConstants.KEY_END;
    static final int KEY_RETURN = InputConstants.KEY_RETURN;
    static final int KEY_NUMPADENTER = InputConstants.KEY_NUMPADENTER;
    static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    static final int KEY_TAB = InputConstants.KEY_TAB;
    static final int KEY_A = InputConstants.KEY_A;
    static final int KEY_V = InputConstants.KEY_V;
    static final int MOUSE_BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    static final int PRESS = InputConstants.PRESS;
    static final int RELEASE = InputConstants.RELEASE;
    static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    //?} else {
    /*static final int KEY_BACKSPACE = GLFW.GLFW_KEY_BACKSPACE;
    static final int KEY_DELETE = GLFW.GLFW_KEY_DELETE;
    static final int KEY_LEFT = GLFW.GLFW_KEY_LEFT;
    static final int KEY_RIGHT = GLFW.GLFW_KEY_RIGHT;
    static final int KEY_UP = GLFW.GLFW_KEY_UP;
    static final int KEY_DOWN = GLFW.GLFW_KEY_DOWN;
    static final int KEY_HOME = GLFW.GLFW_KEY_HOME;
    static final int KEY_END = GLFW.GLFW_KEY_END;
    static final int KEY_RETURN = GLFW.GLFW_KEY_ENTER;
    static final int KEY_NUMPADENTER = GLFW.GLFW_KEY_KP_ENTER;
    static final int KEY_ESCAPE = GLFW.GLFW_KEY_ESCAPE;
    static final int KEY_TAB = GLFW.GLFW_KEY_TAB;
    static final int KEY_A = GLFW.GLFW_KEY_A;
    static final int KEY_V = GLFW.GLFW_KEY_V;
    static final int MOUSE_BUTTON_LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    static final int PRESS = GLFW.GLFW_PRESS;
    static final int RELEASE = GLFW.GLFW_RELEASE;
    static final int MOD_CONTROL = GLFW.GLFW_MOD_CONTROL;
    *///?}
}

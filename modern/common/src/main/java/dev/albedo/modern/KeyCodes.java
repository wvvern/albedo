package dev.albedo.modern;

//? if >=1.17 {
import com.mojang.blaze3d.platform.InputConstants;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}

/** The codes of the keys, the mouse button and the key actions that the screens and the harness read, from the class that has them in this version. */
public final class KeyCodes {
    private KeyCodes() {}

    //? if >=1.17 {
    public static final int KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int KEY_DELETE = InputConstants.KEY_DELETE;
    public static final int KEY_LEFT = InputConstants.KEY_LEFT;
    public static final int KEY_RIGHT = InputConstants.KEY_RIGHT;
    public static final int KEY_UP = InputConstants.KEY_UP;
    public static final int KEY_DOWN = InputConstants.KEY_DOWN;
    public static final int KEY_HOME = InputConstants.KEY_HOME;
    public static final int KEY_END = InputConstants.KEY_END;
    public static final int KEY_RETURN = InputConstants.KEY_RETURN;
    public static final int KEY_NUMPADENTER = InputConstants.KEY_NUMPADENTER;
    public static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int KEY_TAB = InputConstants.KEY_TAB;
    public static final int KEY_A = InputConstants.KEY_A;
    public static final int KEY_V = InputConstants.KEY_V;
    public static final int MOUSE_BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int PRESS = InputConstants.PRESS;
    public static final int RELEASE = InputConstants.RELEASE;
    public static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    //?} else {
    /*public static final int KEY_BACKSPACE = GLFW.GLFW_KEY_BACKSPACE;
    public static final int KEY_DELETE = GLFW.GLFW_KEY_DELETE;
    public static final int KEY_LEFT = GLFW.GLFW_KEY_LEFT;
    public static final int KEY_RIGHT = GLFW.GLFW_KEY_RIGHT;
    public static final int KEY_UP = GLFW.GLFW_KEY_UP;
    public static final int KEY_DOWN = GLFW.GLFW_KEY_DOWN;
    public static final int KEY_HOME = GLFW.GLFW_KEY_HOME;
    public static final int KEY_END = GLFW.GLFW_KEY_END;
    public static final int KEY_RETURN = GLFW.GLFW_KEY_ENTER;
    public static final int KEY_NUMPADENTER = GLFW.GLFW_KEY_KP_ENTER;
    public static final int KEY_ESCAPE = GLFW.GLFW_KEY_ESCAPE;
    public static final int KEY_TAB = GLFW.GLFW_KEY_TAB;
    public static final int KEY_A = GLFW.GLFW_KEY_A;
    public static final int KEY_V = GLFW.GLFW_KEY_V;
    public static final int MOUSE_BUTTON_LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    public static final int PRESS = GLFW.GLFW_PRESS;
    public static final int RELEASE = GLFW.GLFW_RELEASE;
    public static final int MOD_CONTROL = GLFW.GLFW_MOD_CONTROL;
    *///?}
}

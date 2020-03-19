package easing;

import org.joml.Vector2i;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class CurvyGraphEditor {
    private final String WINDOW_TITLE = "Curves";
    private int screenWidth = 1500;
    private int screenHeight = 950;

    private long window;
    private long vg;

    final static int SEGMENTS = 100;

    int mouseX = 0;
    int mouseY = 0;

    int globalOffsetX = 0;
    int globalOffsetY = 0;

    boolean isDraggingWorld = false;
    boolean isDraggingGraph = false;
    boolean isDraggingOutput = false;

    int italicFontID = 0;
    int romanFontID = 0;
    int boldFontID = 0;
    int vegurFontID = 0;

    ByteBuffer cmuSerifBoldFontBuffer;
    ByteBuffer cmuSerifRomanFontBuffer;
    ByteBuffer cmuSerifItalicFontBuffer;
    ByteBuffer vagurRegularFontBuffer;

    float borderGray = 0.35f;
    float borderGray2 = 0.15f;
    NVGColor curveColor = NVGColor.create().r(1f).g(1f).b(1.0f).a(1.0f);
    NVGColor borderColor = NVGColor.create().r(borderGray).g(borderGray).b(borderGray).a(1.0f);
    NVGColor borderColor2 = NVGColor.create().r(borderGray2).g(borderGray2).b(borderGray2).a(1.0f);

    List<Module> curveBoxes;
    Module dragging;

    Panel panel;

    int graphWidth  = 200;
    int graphHeight = 200;

    public void run() {

        long currentTime = System.nanoTime();
        long lastFrame = currentTime;

        init();
        int frames = 0;

        while (!glfwWindowShouldClose(window)){
            currentTime = System.nanoTime();

            draw();
            glfwSwapBuffers(window);
            glfwPollEvents();

            frames++;
            if (currentTime - lastFrame  > 1e9f) {

                glfwSetWindowTitle(window, WINDOW_TITLE + " | FPS: " + frames);
                frames = 0;
                lastFrame = currentTime;
            }
        }

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init () {
        GLFWErrorCallback.createPrint(System.err).set();

        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(screenWidth, screenHeight, WINDOW_TITLE, NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                glfwSetWindowShouldClose(window, true);
        });

        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if ((button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS)) {
                if (mouseX < panel.width) {
                    PanelEntry entry = panel.getEntryAtMouse(mouseX, mouseY);
                    if (entry != null) {
                        Module m = entry.module.duplicate();
                        m.setLocation(getGlobalMousePos().sub(new Vector2i(m.getSize().x / 2, m.getSize().y / 2)));
                        curveBoxes.add(m);

                        isDraggingWorld = false;
                        isDraggingGraph = true;
                        isDraggingOutput = false;
                        dragging = m;
                    }
                } else if (getMouseOverGraph() != null) {
                    dragging = getMouseOverGraph();

                    if (dragging instanceof ConstantSlider) {
                        ConstantSlider slider = (ConstantSlider) dragging;
                        int width = slider.getSize().x;
                        int height = slider.getSize().y;
                        int offsetX = slider.getLocation().x;
                        int offsetY = slider.getLocation().y;
                        int sliderBallx = (int) (offsetX + 10 + slider.getPoint(0.0f) * (width - 2.0f * 10));
                        int sliderBally = (int) (screenHeight - height / 2f - offsetY);
                        float dist = (float) getGlobalMousePos().sub(new Vector2i(sliderBallx, sliderBally)).length();
                        System.out.println(dist);
                    }

                    isDraggingWorld = false;
                    isDraggingGraph = true;
                    isDraggingOutput = false;
                } else if (getMouseOverOutput() != null) {
                    isDraggingWorld = false;
                    isDraggingGraph = false;
                    isDraggingOutput = true;

                    dragging = getMouseOverOutput();

                } else if (getMouseOverInput() != null) {
                    Module input = getMouseOverInput();
                    if (input != null) {
                        int index = input.mouseOverInput(getGlobalMousePos());
                        disconnect(input, index);
                    }
                } else {
                    isDraggingWorld = true;
                    isDraggingGraph = false;
                    isDraggingOutput = false;

                    dragging = null;
                }
            } else if ((button == GLFW_MOUSE_BUTTON_1 && action == GLFW_RELEASE)) {
                if (dragging != null) {
                    if (mouseX < panel.width && !dragging.isConnected()) {
                        curveBoxes.remove(dragging);
                    }
                }

                isDraggingWorld = false;
                isDraggingGraph = false;
                isDraggingOutput = false;

                dragging = null;

            }
        });

        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            if (isDraggingWorld) {
                globalOffsetX += (int) (xpos - mouseX);
                globalOffsetY -= (int) (ypos - mouseY);
            } else if (isDraggingGraph) {
                dragging.setLocation(dragging.getLocation().add(new Vector2i((int) (xpos - mouseX), - (int) (ypos - mouseY))));
            } else if (isDraggingOutput) {
                Module input = getMouseOverInput();
                if (input != null) {
                    int index = input.mouseOverInput(getGlobalMousePos());
                    connect(dragging, input, index);
                    isDraggingWorld = false;
                    isDraggingGraph = false;
                    isDraggingOutput = false;

                    dragging = null;
                }
            }

            mouseX = (int) xpos;
            mouseY = (int) ypos;
        });

        glfwSetFramebufferSizeCallback(window, (handle, w, h) -> {
            screenWidth = w;
            screenHeight = h;
        });

        try ( MemoryStack stack = stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwShowWindow(window);

        GL.createCapabilities();
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);


        cmuSerifItalicFontBuffer = Util.ioResourceToByteBuffer("assets/fonts/cmu/CMUSerifItalic.ttf", 150 * 1024);
        italicFontID = nvgCreateFontMem(vg, "mono", cmuSerifItalicFontBuffer, 0);

        cmuSerifRomanFontBuffer = Util.ioResourceToByteBuffer("assets/fonts/cmu/CMUSerifRoman.ttf", 150 * 1024);
        romanFontID = nvgCreateFontMem(vg, "mono", cmuSerifRomanFontBuffer, 0);

        cmuSerifBoldFontBuffer = Util.ioResourceToByteBuffer("assets/fonts/cmu/CMUSerifBold.ttf", 150 * 1024);
        boldFontID = nvgCreateFontMem(vg, "mono", cmuSerifBoldFontBuffer, 0);

        vagurRegularFontBuffer = Util.ioResourceToByteBuffer("assets/fonts/vegur/Vegur-Regular.otf", 150 * 1024);
        vegurFontID = nvgCreateFontMem(vg, "mono", vagurRegularFontBuffer, 0);

        curveBoxes = new ArrayList<>();

        panel = new Panel(300);
        panel.entries.add(new PanelEntry(new ConstantSlider(0.0f, 1.0f)));
//        panel.entries.add(new PanelEntry(new ConstantSlider(2.0f)));
//        panel.entries.add(new PanelEntry(new ConstantSlider((float) Math.PI)));

        for (Function f : getFunctions())
            panel.entries.add(new PanelEntry(new CurveModule(f)));
    }

    public List<Function> getFunctions () {
        List<Function> functions = new ArrayList<>();

        functions.add(new Function("flip", "t") {
            @Override
            float eval(Float... args) {
                return 1.0f - args[0];
            }

            @Override
            public String getExpression (String... args) {
                return "1.0f - " + args[0];
            }
        });

        functions.add(new Function("quadratic", "t") {
            @Override
            float eval(Float... args) {
                return args[0] * args[0];
            }

            @Override
            public String getExpression (String... args) {
                return "(" + args[0] + ")^2";
            }
        });

        functions.add(new Function("cubic", "t") {
            @Override
            float eval(Float... args) {
                return args[0] * args[0] * args[0];
            }

            @Override
            public String getExpression (String... args) {
                return "(" + args[0] + ")^3";
            }
        });

        functions.add(new Function("quartic", "t") {
            @Override
            float eval(Float... args) {
                return args[0] * args[0] * args[0] * args[0];
            }

            @Override
            public String getExpression (String... args) {
                return "(" + args[0] + ")^4";
            }
        });

        functions.add(new Function("quintic", "t") {
            @Override
            float eval(Float... args) {
                return args[0] * args[0] * args[0] * args[0] * args[0];
            }

            @Override
            public String getExpression (String... args) {
                return "(" + args[0] + ")^5";
            }
        });

        functions.add(new Function("sin", "t") {
            @Override
            float eval(Float... args) {
                return (float) Math.sin(args[0]);
            }
        });

        functions.add(new Function("log", "t") {
            @Override
            float eval(Float... args) {
                return (float) Math.log(args[0]);
            }
        });

        functions.add(new Function("log10", "t") {
            @Override
            float eval(Float... args) {
                return (float) Math.log10(args[0]);
            }
        });

        functions.add(new Function("sqrt", "t") {
            @Override
            float eval(Float... args) {
                return (float) Math.sqrt(args[0]);
            }
        });

        functions.add(new Function("cos", "t") {
            @Override
            float eval(Float... args) {
                return (float) Math.cos(args[0]);
            }
        });

        functions.add(new Function("multiply", "x", "y") {
            @Override
            float eval(Float... args) {
                return args[0] * args[1];
            }

            @Override
            public String getExpression (String... args) {
                return args[0] + " * " + args[1];
            }
        });

        functions.add(new Function("min", "x", "y") {
            @Override
            float eval(Float... args) {
                return Math.min(args[0], args[1]);
            }
        });

        functions.add(new Function("max", "x", "y") {
            @Override
            float eval(Float... args) {
                return Math.max(args[0], args[1]);
            }
        });

        functions.add(new Function("step", "edge", "x") {
            @Override
            float eval(Float... args) {
                return args[0] < args[1] ? 0.0f : 1.0f;
            }
        });

        functions.add(new Function("pow", "x", "y") {
            @Override
            float eval(Float... args) {
                return (float) Math.pow(args[0], args[1]);
            }
            @Override
            public String getExpression (String... args) {
                return "(" + args[0] + ")^(" + args[1] + ")";
            }
        });

        functions.add(new Function("mix", "x", "y", "a") {
            @Override
            float eval(Float... args) {
                return args[0] + args[2] * (args[1] - args[0]);
            }

            @Override
            public String getExpression (String... args) {
                return args[0] + " + " + args[2] + " * (" + args[1] + " - " + args[0] + ")";
            }
        });
        return functions;
    }

    public Module getMouseOverGraph() {
        for (Module curveBox : curveBoxes) {
            if (curveBox.isMouseOverGraph(getGlobalMousePos()))
                return curveBox;
        }
        return null;
    }

    public Module getMouseOverOutput() {
        for (Module curveBox : curveBoxes) {
            if (curveBox.isMouseOverOutput(getGlobalMousePos()))
                return curveBox;
        }
        return null;
    }

    public Module getMouseOverInput() {
        for (Module curveBox : curveBoxes) {
            if (curveBox.mouseOverInput(getGlobalMousePos()) != -1)
                return curveBox;
        }
        return null;
    }

    public Vector2i getGlobalMousePos () {
        return new Vector2i(mouseX - globalOffsetX, screenHeight - mouseY - globalOffsetY);
    }

    public static void connect (Module out, Module in, int index) {
        if (isAcyclic(out, in)) {
            in.setInput(out, index);
            out.children.add(in);
            out.update();
        }
    }

    public static void disconnect (Module in, int index) {
        Module out = in.getInputs()[index];
        if (out != null)
            out.children.remove(in);
        in.setInput(null, index);
        in.update();
    }

    public static boolean isAcyclic(Module root, Module current) {
        if (root.equals(current))
            return false;
        for (Module child : current.children) {
            if(!isAcyclic(root, child))
                return false;
        }
        return true;
    }

    int fontSize = 26;
    public void draw (){
        GL11.glViewport(0, 0, screenWidth, screenHeight);
        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);


        nvgBeginFrame(vg, screenWidth, screenHeight, 1.0f);

        nvgFontBlur(vg, 0);
        nvgFontFaceId(vg, romanFontID);
//        nvgFontSize(vg, 200);
//        nvgText(vg, 500, 100, "π");

        double circleSpeed = 8e6f;
        long time = (long) (System.nanoTime() / circleSpeed);

        for (int i = 0; i < curveBoxes.size(); i++) {
            int circleIndex = (int) (time % SEGMENTS);
            int circleRadius = 5;
            drawModule(curveBoxes.get(i), circleIndex, circleRadius);
        }

        drawPane(panel);

        nvgEndFrame(vg);
    }

    public void drawPane (Panel panel) {

        float gray = 0.15f;
        nvgBeginPath(vg);
        nvgFillColor(vg, rgba(gray, gray, gray, 1.0f, curveColor));
        nvgRect(vg, 0,0, panel.width, screenHeight);
        nvgFill(vg);

        nvgFontBlur(vg, 0);
        nvgFillColor(vg, rgba(1.0f, 1.0f, 1.0f, 1.0f, curveColor));
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);

        nvgFontSize(vg, panel.titleFontSize);

        int i = 0;
        for (PanelEntry entry : panel.entries) {
            nvgFontFaceId(vg, vegurFontID);
            nvgText(vg, panel.panelLeftMargin,
                    panel.panelTopMargin + i * (panel.titleFontSize + panel.listMargin),
                    entry.module.name);
            i++;
        }

//        nvgFontFaceId(vg, italicFontID);
//        nvgText(vg, 50, 10, "(x, y, a)");
    }

    public void drawModule (Module module, int index, int radius) {
        int width = module.getSize().x;
        int height = module.getSize().y;
        int offsetX = globalOffsetX + module.getLocation().x;
        int offsetY = globalOffsetY + module.getLocation().y;
        Module[] inputs = module.getInputs();

        if (module instanceof CurveModule) {
            if (offsetX + module.size.x > 0 && offsetX < screenWidth
             && offsetY + module.size.y > 0 && offsetY < screenHeight)
                drawGraph(module, index, radius);
        }
        if (module instanceof ConstantSlider)
            drawSlider(module);
        String text = module.getName();

        //Draw Output Ball
        nvgBeginPath(vg);
        nvgFillColor(vg, rgba(1.0f, 1.0f, 1.0f, 1.0f, curveColor));
//        eqTriangle(vg, offsetX + width + 10, (int) (screenHeight - height / 2f - offsetY), radius);
        nvgCircle(vg, offsetX + width + 10,screenHeight - height / 2f - offsetY, radius);
        nvgFill(vg);

        //Draw dragged line
        if (isDraggingOutput && module.equals(dragging)) {
            nvgBeginPath(vg);
            nvgStrokeColor(vg, rgba(1.0f, 1.0f, 1.0f, 1.0f, curveColor));
            bezier(vg, offsetX + width + 10,screenHeight - height / 2f - offsetY, mouseX, mouseY);
            nvgStroke(vg);
        }

        //Draw Input Balls
        for (int i = 0; i < inputs.length; i++) {
            nvgBeginPath(vg);
            nvgFillColor(vg, rgba(1.0f, 1.0f, 1.0f, 1.0f, curveColor));
            nvgCircle(vg, offsetX - 10,screenHeight - height * (i + 1) / (float)(inputs.length + 1)  - offsetY, radius);
            nvgFill(vg);

            if (inputs[i] != null) {
                Module inputBox = inputs[i];
                int inputWidth = inputBox.getSize().x;
                int inputHeight = inputBox.getSize().y;
                int inputOffsetX = globalOffsetX + inputBox.getLocation().x;
                int inputOffsetY = globalOffsetY + inputBox.getLocation().y;

                nvgBeginPath(vg);
                nvgStrokeColor(vg, rgba(1.0f, 1.0f, 1.0f, 1.0f, curveColor));
                bezier(vg, offsetX - 10, screenHeight - height * (inputs.length - i) / (float)(inputs.length + 1)  - offsetY, inputOffsetX + inputWidth + 10, screenHeight - inputHeight / 2f - inputOffsetY);
                nvgStroke(vg);
            }
        }

        //Draw Curve Name
        nvgFontSize(vg, 26);
        nvgFillColor(vg, curveColor);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_TOP);
        nvgText(vg, offsetX + width / 2f, screenHeight - offsetY, text);
    }

    public void drawSlider (Module module) {
        int width = module.getSize().x;
        int height = module.getSize().y;
        int offsetX = globalOffsetX + module.getLocation().x;
        int offsetY = globalOffsetY + module.getLocation().y;
        int innerMargin = 10;

        nvgBeginPath(vg);
        nvgStrokeColor(vg, borderColor);
        nvgMoveTo(vg, offsetX,screenHeight - height - offsetY);
        nvgLineTo(vg, offsetX + width,screenHeight - height - offsetY);
        nvgLineTo(vg, offsetX + width,screenHeight - offsetY);
        nvgLineTo(vg, offsetX,screenHeight - offsetY);
        nvgLineTo(vg, offsetX,screenHeight - height - offsetY);
        nvgStroke(vg);

        nvgBeginPath(vg);
        nvgStrokeColor(vg, borderColor);
        nvgMoveTo(vg, offsetX + innerMargin,screenHeight - height / 2f - offsetY);
        nvgLineTo(vg, offsetX - innerMargin + width,screenHeight - height / 2f - offsetY);
        nvgStroke(vg);

        nvgBeginPath(vg);
        nvgFillColor(vg, rgba(1.0f, 1.0f, 1.0f, 1.0f, curveColor));
        nvgCircle(vg, offsetX + innerMargin + module.getPoint(0.0f) * (width - 2.0f * innerMargin), screenHeight - height / 2f - offsetY, 5);
        nvgFill(vg);
    }

    public void drawGraph (Module box, int index, int radius) {
        int width = box.getSize().x;
        int height = box.getSize().y;
        int offsetX = globalOffsetX + box.getLocation().x;
        int offsetY = globalOffsetY + box.getLocation().y;
        List<Float> curve = ((CurveModule) box).getCurve();

        //Draw Grid
        nvgBeginPath(vg);
        nvgStrokeColor(vg, borderColor);
        nvgMoveTo(vg, offsetX,screenHeight - height - offsetY);
        nvgLineTo(vg, offsetX + width,screenHeight - height - offsetY);
        nvgLineTo(vg, offsetX + width,screenHeight - offsetY);
        nvgLineTo(vg, offsetX,screenHeight - offsetY);
        nvgLineTo(vg, offsetX,screenHeight - height - offsetY);
        nvgStroke(vg);

        nvgBeginPath(vg);
        nvgMoveTo(vg, offsetX,screenHeight - height / 2f - offsetY);
        nvgLineTo(vg, offsetX + width,screenHeight - height / 2f - offsetY);
        nvgStroke(vg);

        nvgBeginPath(vg);
        nvgMoveTo(vg, offsetX + width / 2f,screenHeight - height - offsetY);
        nvgLineTo(vg, offsetX + width / 2f,screenHeight - offsetY);
        nvgStroke(vg);

        nvgStrokeColor(vg, borderColor2);
        nvgBeginPath(vg);
        nvgMoveTo(vg, offsetX + width / 4f,screenHeight - height - offsetY);
        nvgLineTo(vg, offsetX + width / 4f,screenHeight - offsetY);
        nvgStroke(vg);

        nvgBeginPath(vg);
        nvgMoveTo(vg, offsetX + width * 3f / 4f,screenHeight - height - offsetY);
        nvgLineTo(vg, offsetX + width * 3f / 4f,screenHeight - offsetY);
        nvgStroke(vg);

        nvgBeginPath(vg);
        nvgMoveTo(vg, offsetX,           screenHeight - height / 4f - offsetY);
        nvgLineTo(vg, offsetX + width,screenHeight - height / 4f - offsetY);
        nvgStroke(vg);

        nvgBeginPath(vg);
        nvgMoveTo(vg, offsetX,           screenHeight - height * 3f / 4f - offsetY);
        nvgLineTo(vg, offsetX + width,screenHeight - height * 3f / 4f - offsetY);
        nvgStroke(vg);

        //Draw Curve
        for (int i = 1; i < curve.size(); i++) {
            float alpha = mod(index - i,curve.size() - 1) / (float) (curve.size() - 1);
            alpha = 1.0f - alpha;
            nvgBeginPath(vg);
            nvgStrokeColor(vg, rgba(1.0f, 1.0f, 1.0f, alpha, curveColor));
            float p0 = normalisePoint(curve.get(i - 1), box.getMin(), box.getMax());
            float p1 = normalisePoint(curve.get(i    ), box.getMin(), box.getMax());
            nvgMoveTo(vg, offsetX + width * (float) (i-1) / (float) (curve.size() - 1), screenHeight - p0 * height - offsetY);
            nvgLineTo(vg, offsetX + width * (float) i / (float) (curve.size() - 1), screenHeight - p1 * height - offsetY);
            nvgStroke(vg);
        }

        //Draw Curve Ball
        if (curve.size() > 0) {
            nvgBeginPath(vg);
            nvgFillColor(vg, rgba(1.0f, 1.0f, 1.0f, 1.0f, curveColor));
            float p0 = normalisePoint(curve.get(index), box.getMin(), box.getMax());
            nvgCircle(vg, offsetX + width * (float) index / (float) (curve.size() - 1), screenHeight - p0 * height - offsetY, radius);
            nvgFill(vg);
        }

    }

    public static float normalisePoint (float point, float min, float max) {
        if (max == min)
            return 1.0f;
        return (point - min) / (max - min);
    }

    public static void eqTriangle (long vg, int x, int y, int radius) {
        float angle = (float) (2 / 3f * Math.PI);
        nvgMoveTo(vg, (float) (x + Math.cos(angle * 0) * radius), (float) (y + Math.sin(angle * 0) * radius));
        nvgLineTo(vg, (float) (x + Math.cos(angle * 1) * radius), (float) (y + Math.sin(angle * 1) * radius));
        nvgLineTo(vg, (float) (x + Math.cos(angle * 2) * radius), (float) (y + Math.sin(angle * 2) * radius));
        nvgLineTo(vg, (float) (x + Math.cos(angle * 3) * radius), (float) (y + Math.sin(angle * 3) * radius));
    }

    public static void bezier (long vg, float x1, float y1, float x2, float y2) {
        nvgMoveTo(vg, x1, y1);
        nvgBezierTo(vg, (x1 + x2) / 2f, y1, (x1 + x2) / 2f, y2, x2, y2);
    }

    public static int mod(int x, int y) {
        int result = x % y;
        return result < 0? result + y : result;
    }

    public static NVGColor rgba(float r, float g, float b, float a, NVGColor color) {
        color.r(r);
        color.g(g);
        color.b(b);
        color.a(a);

        return color;
    }

    public static void main (String[] args) {
        new CurvyGraphEditor().run();
    }
}


class Panel {
    int width;
    List<PanelEntry> entries;

    int panelLeftMargin = 15;
    int panelTopMargin = 15;
    int titleFontSize = 24;
    int listMargin = 2;
    int descFontSize = 22;

    public Panel (int width) {
        this.width = width;
        this.entries = new ArrayList<>();
    }

    public PanelEntry getEntryAtMouse (int mouseX, int mouseY) {
        int index = (mouseY - panelTopMargin) / (titleFontSize + listMargin);
        System.out.println(index);
        return entries.get(index);
    }
}

class PanelEntry {
    Module module;
    String description;
    public PanelEntry (Module module) {
        this.module = module;
    }
}
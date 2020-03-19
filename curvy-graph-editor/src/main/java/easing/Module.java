package easing;

import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Module {
    Vector2i location;
    Vector2i size;
    String name;
    Set<Module> children;

    abstract float getPoint (float point);
    abstract Module[] getInputs();
    abstract void setInput(Module input, int i);
    abstract Module duplicate();
    abstract float getMax();
    abstract float getMin();
    abstract void update();
    abstract boolean isConnected();

    public String getName() {
        return name;
    }

    public Vector2i getLocation() {
        return location;
    }

    public void setSize(Vector2i size) {
        this.size = size;
    }

    public Vector2i getSize() {
        return size;
    }

    public void setLocation(Vector2i location) {
        this.location = location;
    }

    boolean isMouseOverGraph(Vector2i mouse) {
        return mouse.x > location.x
                && mouse.y > location.y
                && mouse.x < location.x + size.x
                && mouse.y < location.y + size.y;

    }

    boolean isMouseOverOutput(Vector2i mouse) {
        Vector2i outputBall = new Vector2i(location.x + size.x + 10, (int) (size.y / 2f + location.y));
        return new Vector2i(mouse).sub(outputBall).length() < 10.0f;

    }

    int mouseOverInput(Vector2i mouse) {
        Module[] inputs = this.getInputs();
        for (int i = 0; i < inputs.length; i++) {
            Vector2i inputBall = new Vector2i(location.x -  10, (int) (size.y * (inputs.length - i) / (float)(inputs.length + 1) + location.y));
            if (new Vector2i(mouse).sub(inputBall).length() < 10.0f)
                return i;
        }
        return -1;
    }
}

class ConstantSlider extends Module {
    float constant;
    float min, max;
    public ConstantSlider(float min, float max) {
        this.max = max;
        this.min = min;
        this.constant = (max - min) / 2.0f;
        this.name = getName();
        this.location = new Vector2i(0, 0);
        this.size = new Vector2i(200, 25);
        this.children = new HashSet<>();
    }

    public float getPoint (float point) {
        return constant;
    }

    @Override
    Module[] getInputs() {
        return new Module[]{};
    }

    @Override
    void setInput(Module input, int i) {}

    public ConstantSlider duplicate () {
        return new ConstantSlider(min, max);
    }

    @Override
    float getMax() {
        return constant;
    }

    @Override
    public float getMin () {
        return constant;
    }

    @Override
    void update() {

        for (Module child : children)
            child.update();

    }

    @Override
    boolean isConnected() {
        return this.children.size() > 0;
    }

    @Override
    public String getName () {
        return String.valueOf(this.constant);
    }

}
class CurveModule extends Module {
    Function f;
    String[] expressionArgs;

    Module[] inputs;
    float min = 0.0f;
    float max = 0.0f;

    List<Float> points = new ArrayList<>();

    public CurveModule (Function f) {
        this.name = f.name;
        this.f = f;
        this.expressionArgs = f.args;

        this.location = new Vector2i(0, 0);
        this.size = new Vector2i(200, 200);
        this.inputs = new Module[expressionArgs.length];
        this.children = new HashSet<>();
        this.update();
    }

    public float getPoint (float point) {
        float p;
        Float[] args = new Float[expressionArgs.length];
        for (int i = 0; i < inputs.length; i++) {
            if(inputs[i] != null)
                p = inputs[i].getPoint(point);
            else
                p = point;
            args[i] = p;
        }

        p = f.eval(args);
        min = Math.min(p, min);
        max = Math.max(p, max);
        return p;
    }

    @Override
    Module[] getInputs() {
        return inputs;
    }

    @Override
    void setInput(Module input, int i) {
        inputs[i] = input;
    }

    public CurveModule duplicate () {
        return new CurveModule(this.f);
    }

    @Override
    float getMax() {
        return max;
    }

    @Override
    float getMin() {
        return min;
    }

    public List<Float> getCurve () {
        return points;
    }

    public void update () {
        points = new ArrayList<>();

        for (int i = 0; i < CurvyGraphEditor.SEGMENTS; i++)
            points.add(getPoint((float) i / (float) (CurvyGraphEditor.SEGMENTS - 1)));

        for (Module child : children)
            child.update();
    }

    @Override
    boolean isConnected() {
        boolean ret = this.children.size() > 0;
        for (Module input : inputs)
            ret |= input != null;
        return ret;
    }
}

abstract class Function {
    public final String name;
    public final String[] args;

    public Function (String name, String... args) {
        this.name = name;
        this.args = args;
    }

    abstract float eval (Float... args);

    public String getExpression (String... args) {
        String s = name + "(";
        for (int i = 0; i < args.length - 1; i++)
            s += args[i];
        return s + args[args.length - 1] + ")";
    }
}
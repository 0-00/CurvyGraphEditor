package easing;

import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class Util {
    public static float lerp (float a, float b, float t) {
        return a + t * (b - a);
    }

    public static Vector3f toVector3 (Vector4f v){
        return new Vector3f(v.x, v.y, v.z);
    }

    public static void print(Object s) {
        System.out.println(s.toString());
    }


    public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize){
        ByteBuffer buffer = null;
        File file = new File(resource);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            FileChannel fc = fis.getChannel();
            buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            fc.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }
}

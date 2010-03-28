package org.cat;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.CyclicBarrier;

/**
 * Short description.
 * <p/>
 * Detail description.
 * @author <a href="mailto:striped@gmail.com">Val</a>
 * @version $Revision: 1.0 $ $Date: Mar 27, 2010 11:00:55 PM $
 * @created Mar 28, 2010 11:00:55 PM by striped
 * @todo add JavaDoc
 */
/**
 * Short description.
 * <p/>
 * Detail description.
 * @author <a href="mailto:striped@gmail.com">Val</a>
 * @version $Revision: 1.0 $ $Date: Mar 22, 2010 11:00:55 PM $
 * @created Mar 22, 2010 11:00:55 PM by striped
 * @todo add JavaDoc
 */
public class DirectImageBuilder {

    static final Unsafe unsafe = AccessController.doPrivileged(new GetUnsafeAction());

    final long address;
    final long limit;

    final int width;
    final int height;

    public DirectImageBuilder(final int width, final int height) {
        this.width = width;
        this.height = height;
        this.limit = (width * height) << 2;
        this.address = unsafe.allocateMemory(limit);
    }

    public final int getWidth() {
        return width;
    }

    public final int getHeight() {
        return height;
    }

    public final void attach(final Performer performer) {
        performer.init(this, 0, height);
    }

    public final void attach(final Performer[] performer, final CyclicBarrier barrier) {
        final int parts = performer.length;
        int step = height / parts;
        int from, i;
        for (from = height, i = parts; i-->1; from -= step) {
            performer[i].init(this, from - step, from);
            performer[i].setBarrier(barrier);
        }
        performer[i].init(this, 0, from);
        performer[i].setBarrier(barrier);
    }

    public void dispose() {
        unsafe.freeMemory(address);
    }

    private static class GetUnsafeAction implements PrivilegedAction<Unsafe> {
        public Unsafe run() {
            try {
                final Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                return (Unsafe)field.get(null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class SequentialPerformer extends Performer {

        public void run() {
            for (int y = to; y-->from;) {
                for (int x = width; x-->0;) {
                    pointer -= 4;
                    perform(x, y);
                }
            }
            super.run();
        }

        protected final void addPixel(final int rgb) {
            unsafe.putInt(pointer, rgb);
        }

        protected final int getPixel() {
            return unsafe.getInt(pointer);
        }

        protected abstract void perform(final int x, final int y);
    }

    static class ConvolutionPerfrormer extends Performer implements Runnable {
        private static final int[] HX = {
                -1, 0, 1,
                -2, 0, 2,
                -1, 0, 1
        };
        private static final int[] HY = {
                -1, -2, -1,
                 0,  0,  0,
                 1,  2,  1
        };
        private static final int minColor = (ByteOrder.BIG_ENDIAN == ByteOrder.nativeOrder())? 1: 0;

        private long storageAddress;
        private long storagePointer;

        public ConvolutionPerfrormer(final DirectImageBuilder builder) {
            this.storageAddress = builder.address;
        }

        public void init(final DirectImageBuilder builder, final int from, final int to) {
            this.width = builder.width;
            this.from = (0 < from)? from: from + 1;
            this.to = (to < builder.height)? to: to - 1;
            this.address = builder.address;
            reset();
        }

        public void reset() {
            final int limit = (this.to - 1) * width << 2;
            this.pointer = address + limit;
            this.storagePointer = storageAddress + limit;
        }

        public void run() {
            int rgbX, rgbY;
            byte comp;
            for (int y = to; y-->from; pointer -= 2 << 2, storagePointer -= 2 << 2) {
                for (int x = width - 1; x-->1;) {
                    pointer -= 4;
                    storagePointer -= 4;
                    for (int c = minColor + 4; c-->minColor;) {
                        rgbX = rgbY = 0;
                        for(int i = HX.length; i-->0;) {
                            comp = getKernel(i, c);
                            rgbX += HX[i] * comp;
                            rgbY += HY[i] * comp;
                        }
                        rgbX = Math.abs(rgbX) + Math.abs(rgbY);
                        unsafe.putByte(storagePointer + c, (byte) ((0xff < rgbX)? 0xff: rgbX));
                    }
                }
            }
            super.run();
        }

        private byte getKernel(final int idx, final int color) {
            int row = (idx / 3) - 1;  //current row in kernel
            int column = (idx % 3) - 1; //current column in kernel
            return unsafe.getByte(pointer + color - ((row * width - column) << 2));
        }
    }
}

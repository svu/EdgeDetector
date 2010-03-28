package org.cat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Short description.
 * <p/>
 * Detail description.
 * @author <a href="mailto:striped@gmail.com">Val</a>
 * @version $Revision: 1.0 $ $Date: Mar 27, 2010 6:01:04 PM $
 * @created Mar 28, 2010 6:01:04 PM by striped
 * @todo add JavaDoc
 */
public class Measurer {
    private final DirectImageBuilder in;
    private DirectImageBuilder out;
    private final int threadNum;
    private final CyclicBarrier barrier;
    private int imageType;

	public Measurer(final File file, final int num) throws IOException {
        threadNum = num;
        barrier = new CyclicBarrier(num + 1); // parts + main thread
        final BufferedImage image = ImageIO.read(file);
        imageType = image.getType();
        in = new DirectImageBuilder(image.getWidth(), image.getHeight());
        final Performer loader = new DirectImageBuilder.SequentialPerformer() {
            @Override
            protected void perform(final int x, final int y) {
                addPixel(image.getRGB(x, y));
            }
        };
        in.attach(loader);
        loader.run();
        out = new DirectImageBuilder(in.getWidth(), in.getHeight());
    }

    public void detectEdge() throws TimeoutException {
        final Performer[] detectors = new Performer[threadNum];
        for (int i = detectors.length; i-->0;) {
            detectors[i] = new DirectImageBuilder.ConvolutionPerfrormer(out);
        }
        in.attach(detectors, barrier);
        runParallel(detectors, barrier);
    }

    public void measure(int warmingMultiplier, int maxIteration) throws TimeoutException {
        final Performer[] detectors = new Performer[threadNum];
        for (int i = detectors.length; i-->0;) {
            detectors[i] = new DirectImageBuilder.ConvolutionPerfrormer(out);
        }
        in.attach(detectors, barrier);

        System.out.println("Warming up...");
        final int times = warmingMultiplier * maxIteration;
        for (int k = times; k-->0;) {
            runParallel(detectors, barrier);
            if (0 == k % 10) {
                System.out.print("  " + (100 - k * 100 / times) + "%\r");
            }
        }
        System.out.print("Measure...");
        long time = System.nanoTime();
        for (int k = maxIteration; k-->0;) {
            runParallel(detectors, barrier);
        }
        time = System.nanoTime() - time;
        System.out.println(String.format(
                "Done.\nElapsed %d ms (%.3f ms per each)",
                TimeUnit.NANOSECONDS.toMillis(time),
                (double) TimeUnit.NANOSECONDS.toMillis(time) / maxIteration
        ));
    }

    private static void runParallel(final Performer[] detectors, final CyclicBarrier barrier) throws TimeoutException {
        barrier.reset();
        for (int i = detectors.length; i-->0;) {
            detectors[i].reset();
            new Thread(detectors[i]).start();
        }
        try {
            barrier.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignore
        } catch (BrokenBarrierException e) {
            // ignore
        } catch (TimeoutException e) {
            System.out.println(String.format("Interrupted by %d still waiting", barrier.getNumberWaiting()));
            throw e;
        }
    }

    public void save(final File file) throws IOException {
        final BufferedImage image = new BufferedImage(out.getWidth(), out.getHeight(), imageType);
        final Performer saver = new DirectImageBuilder.SequentialPerformer() {
            @Override
            protected void perform(final int x, final int y) {
                image.setRGB(x, y, getPixel());
            }
        };
        out.attach(saver);
        saver.run();
        ImageIO.write(image, "PNG", file);
    }

    public void dispose() {
        if (null != in) {
            in.dispose();
        }
        if (null != out) {
            out.dispose();
        }
    }

	public static void main(final String[] args) throws IOException, TimeoutException {
        final File in = new File(args[0]);
        final File out = new File(args[1]);
        final Measurer measurer = new Measurer(in, Integer.parseInt(args[3]));
        try {
            measurer.detectEdge();
            measurer.save(out);
            measurer.measure(100, Integer.parseInt(args[2]));
        } finally {
            measurer.dispose();
        }
	}
}

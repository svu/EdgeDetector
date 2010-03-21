import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import javax.imageio.ImageIO;

public class EdgeDetector {
	private long start;
	private long last;

	private int newPixels[];

	private final String inName;
	private final String outName;
	private final CyclicBarrier barrier;

	public int[] getNewPixels() {
		return newPixels;
	}

	public EdgeDetector(final String inName, final String outName, final int num) {
		this.inName = inName;
		this.outName = outName;
		this.barrier = new CyclicBarrier(num + 1);
	}

	public void reportProgress(String action) {
		final long now = System.nanoTime();
		System.out.println(action + ", " + (now - start) + ", " + (now - last));
		last = now;
	}

	public BufferedImage load(String filename) throws IOException {
		final BufferedImage rv = ImageIO.read(new File(filename));
		reportProgress("Loaded");
		return rv;
	}

	public void save(BufferedImage newImage, String filename) throws IOException {
		ImageIO.write(newImage, "PNG", new File(filename));
		reportProgress("Written");
	}

	public void detect() throws IOException {
		last = start = System.nanoTime();
		System.out.println("Started");

		final BufferedImage origImage = load(inName);
		final BufferedImage newImage = process(origImage);
		save(newImage, outName);
	}

	public BufferedImage process(BufferedImage image) {
		final int width = image.getWidth();
		final int height = image.getHeight();
		newPixels = new int[width * height];

		int parts = barrier.getParties() - 1;
		int step = height / parts;
		int high = Processor.BORDER_WIDTH, low = step;
		for (int i = 0; i < parts; i++) {
			if (low > (height - Processor.BORDER_WIDTH))
				low = height - Processor.BORDER_WIDTH;
			final Processor p = new Processor(this, image, high, low);
			new Thread(p).start();

			high = low;
			low += step;
		}

		try {
			barrier.await();
		} catch (InterruptedException ex) {
		} catch (BrokenBarrierException ex) {
		}

		final BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		newImage.setRGB(0, 0, width, height, newPixels, 0, width);
		reportProgress("Processed");
		return newImage;
	}

	public static void main(String args[]) {

		try {
			final String infile = args[0];
			final String outfile = args[1];
			final int times = Integer.parseInt(args[2]);
			final int numThreads = Integer.parseInt(args[3]);

			System.out.println("Converting from " + infile + " to " + outfile + ", repeat " + times + " times, using " + numThreads + " threads");
			final EdgeDetector ed = new EdgeDetector(infile, outfile, numThreads);

			for (int i = 0; i < times; i++) {
				ed.detect();
			}
		} catch (IOException ex) {
			System.err.println(ex);
			ex.printStackTrace();
		}
	}

	public CyclicBarrier getBarrier() {
		return barrier;
	}

}

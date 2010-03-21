import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class EdgeDetector {
	public final static int BORDER_WIDTH = 1;

	private final long start;
	private long last;

	private int newPixels[];

	public int[] getNewPixels() {
		return newPixels;
	}

	private int partsToDo;

	public void partDone() {
		partsToDo--;
	}

	public EdgeDetector() {
		last = start = System.currentTimeMillis();
		System.out.println("Started");
	}

	public void reportProgress(String action) {
		final long now = System.currentTimeMillis();
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

	public BufferedImage process(BufferedImage image, int parts) {
		final int width = image.getWidth();
		final int height = image.getHeight();
		newPixels = new int[width * height];

		partsToDo = parts;
		int step = height / parts;
		int high = BORDER_WIDTH, low = step;
		for (int i = 0; i < parts; i++) {
			if (low > (height - BORDER_WIDTH))
				low = height - BORDER_WIDTH;
			final Processor p = new Processor(this, image, high, low);
			new Thread(p).start();

			high = low;
			low += step;
		}

		try {
			synchronized (image) {
				while (partsToDo > 0) {
					image.wait();
				}
			}
		} catch (InterruptedException ex) {
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
			for (int i = 0; i < times; i++) {
				final EdgeDetector ed = new EdgeDetector();
				final BufferedImage origImg = ed.load(infile);
				final BufferedImage newImg = ed.process(origImg, numThreads);
				ed.save(newImg, outfile);
			}
		} catch (IOException ex) {
			System.err.println(ex);
			ex.printStackTrace();
		}
	}

}

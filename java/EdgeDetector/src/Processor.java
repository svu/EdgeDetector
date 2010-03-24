import java.awt.image.BufferedImage;

public class Processor implements Runnable {
	public final static int BORDER_WIDTH = 1;
	private final static int NCOLORS = 3; // R G B

	private int high, low;
	private BufferedImage image;
	private EdgeDetector ed;

	public Processor(EdgeDetector ed, BufferedImage image, int high, int low) {
		this.image = image;
		this.high = high - BORDER_WIDTH;
		this.low = low - BORDER_WIDTH;
		this.ed = ed;
	}

	private static int saturate(int val, int mask) {
		val = Math.abs(val);
		return (val > mask) ? mask : val;
	}

	public void run() {
		int[] newPixels = ed.getNewPixels();
		int[] oldPixels = ed.getOldPixels();
		final int width = image.getWidth();
		int newPixelsOffset = BORDER_WIDTH + (width * (high + BORDER_WIDTH));

		final int doubleBorder = BORDER_WIDTH * 2;
		final int doubleWidth = width * 2;
		final int xLimit = width - doubleBorder;

		int srcPos = width * high;
		for (int iy = low - high; --iy >= 0; newPixelsOffset += doubleBorder, srcPos += doubleBorder) {
			for (int ix = xLimit; --ix >= 0; srcPos++) {

				final int srcPos1 = srcPos + width;
				final int srcPos2 = srcPos + doubleWidth;
				final int d11 = oldPixels[srcPos];
				final int d12 = oldPixels[srcPos + 1];
				final int d13 = oldPixels[srcPos + 2];
				final int d21 = oldPixels[srcPos1];
				// no need in d22
				final int d23 = oldPixels[srcPos1 + 2];
				final int d31 = oldPixels[srcPos2];
				final int d32 = oldPixels[srcPos2 + 1];
				final int d33 = oldPixels[srcPos2 + 2];
				int mask = 0xFF;
				int pixel = 0;
				// B, then G, then R
				for (int c = NCOLORS; --c >= 0; mask <<= 8) {
					final int common = -(d11 & mask) + (d33 & mask);
					// -1 0 1 -2 0 2 -1 0 1
					final int h = saturate(common + (d13 & mask) - (d21 & mask) * 2 + (d23 & mask) * 2 - (d31 & mask), mask);
					// -1, -2, -1, 0, 0, 0, 1, 2, 1
					final int v = saturate(common - (d12 & mask) * 2 - (d13 & mask) + (d31 & mask) + (d32 & mask) * 2, mask);
					pixel |= (h > v) ? h : v;
				}

				newPixels[newPixelsOffset++] = pixel;
			}
		}

		try {
			ed.getBarrier().await();
		} catch (Exception ex) {
		}
	}
}

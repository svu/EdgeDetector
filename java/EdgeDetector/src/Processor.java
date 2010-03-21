import java.awt.image.BufferedImage;
import java.util.Arrays;

public class Processor implements Runnable {
	public final static int BORDER_WIDTH = 1;
	private final static int NCOLORS = 3; // R G B

	private final int H[] = { -1, 0, 1, -2, 0, 2, -1, 0, 1 };
	private final int V[] = { -1, -2, -1, 0, 0, 0, 1, 2, 1 };

	private int high, low;
	private BufferedImage image;
	private EdgeDetector ed;

	public Processor(EdgeDetector ed, BufferedImage image, int high, int low) {
		this.image = image;
		this.high = high;
		this.low = low;
		this.ed = ed;
	}

	private final static int saturate(int val) {
		return (val < 0) ? 0 : (val >= 0x100) ? 0xFF : val;
	}

	public void run() {
		int[] newPixels = ed.getNewPixels();
		final int width = image.getWidth();
		int newPixelsOffset = BORDER_WIDTH + (width * high);

		// [0]:B [1]:G [2]:R
		final int colorValuesH[] = new int[NCOLORS];
		final int colorValuesV[] = new int[NCOLORS];

		final int sampleWidth = 1 + (BORDER_WIDTH << 1);
		final int sampleSize = sampleWidth * sampleWidth;
		final int samplePixels[] = new int[sampleSize];

		for (int y = high; y < low; y++, newPixelsOffset += (BORDER_WIDTH << 1)) {
			for (int x = BORDER_WIDTH; x < width - BORDER_WIDTH; x++) {
				// TYPE_INT_ARGB
				image.getRGB(x - BORDER_WIDTH, y - BORDER_WIDTH, sampleWidth, sampleWidth, samplePixels, 0, sampleWidth);

				Arrays.fill(colorValuesH, 0);
				Arrays.fill(colorValuesV, 0);
				for (int o = 0; o < sampleSize; o++) {
					int pixel = samplePixels[o];
					// Process bytes in B, G, R order
					for (int c = 0; c < NCOLORS; c++) {
						final int val = pixel & 0xFF;
						colorValuesH[c] += val * H[o];
						colorValuesV[c] += val * V[o];
						pixel >>= 8;
					}
				}
				int pixel = 0;
				// R, then G, then B
				for (int c = NCOLORS; --c >= 0;) {
					final int h = saturate(colorValuesH[c]);
					final int v = saturate(colorValuesV[c]);
					pixel <<= 8;
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
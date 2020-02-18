package de.tuebingen.sfs.psl.util.color;

import java.awt.Color;

public class ColorUtils {
	public static String hsvToRgb(double hue, double saturation, double value) {
        //System.err.print("hvsToRgb(" + hue + "," + saturation + "," + value + ") = ");
		int h = (int) (hue / 60);
		double f = hue / 60 - h;
		double p = value * (1 - saturation);
		double q = value * (1 - f * saturation);
		double t = value * (1 - (1 - f) * saturation);

		switch (h) {
			case 0:
				return rgbToString(value, t, p);
			case 1:
				return rgbToString(q, value, p);
			case 2:
				return rgbToString(p, value, t);
			case 3:
				return rgbToString(p, q, value);
			case 4:
				return rgbToString(t, p, value);
			case 5:
				return rgbToString(value, p, q);
			default:
				throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
		}
	}

	public static String rgbToString(double r, double g, double b) {
		//System.err.print("rgbToString(" + r + "," + g + "," + b + ") = ");
		String rs = Integer.toHexString((int) (r * 255));
		String gs = Integer.toHexString((int) (g * 255));
		String bs = Integer.toHexString((int) (b * 255));
		if (rs.length() < 2) rs = "0" + rs;
		if (gs.length() < 2) gs = "0" + gs;
		if (bs.length() < 2) bs = "0" + bs;
		String resultString = "#" + rs + gs + bs;
		//System.err.println(resultString);
		return resultString;
	}
	
	/**
     * Utility method to format a color to HTML RGB color format (e.g. #FF0000 for Color.red).
     * @param color The color.
     * @return the HTML RGB color string.
     */
    public static final String colorHTML(Color c) {
        String r = (c.getRed() < 16) ? "0" + Integer.toHexString(c.getRed()) : Integer.toHexString(c.getRed());
        String g = (c.getGreen() < 16) ? "0" + Integer.toHexString(c.getGreen()) : Integer.toHexString(c.getGreen());
        String b = (c.getBlue() < 16) ? "0" + Integer.toHexString(c.getBlue()) : Integer.toHexString(c.getBlue());
        return "#" + r + g + b;
    }
}

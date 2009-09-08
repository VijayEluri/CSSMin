import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.lang.*;

public class CSSMin {

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("You need to supply an input file, and optionally an output file (output is to stdout otherwise).");
			return;
		}
		
		PrintStream out;
		
		if (args.length > 1) {
			try {
				out = new PrintStream(args[1]);
			} catch (Exception e) {
				System.out.println("Error outputting to " + args[1] + "; redirecting to stdout");
				out = System.out;
			}
		} else {
			out = System.out;
		}
		
		formatFile(args[0], out);
	}
	
	public static void formatFile(String f, PrintStream out) {
		try {
			int k, n;
			
			//System.out.println("Reading file contents...");
			
			BufferedReader br = new BufferedReader(new FileReader(f));
			StringBuffer sb = new StringBuffer();
			
			String s;
			while ((s = br.readLine()) != null) {
				if (s.trim().equals("")) continue;
				sb.append(s.replaceAll("[\t\n\r]","").replaceAll("  ", " "));
			}
			//System.out.println("Finished reading file contents.");
			
			
			//System.out.println("Stripping comments...");
			// Find the start of the comment
			while ((n = sb.indexOf("/*")) != -1) {
				k = sb.indexOf("*/", n + 2);
				if (k == -1) {
					throw new Exception("Error: Unterminated comment.");
				}
				sb.delete(n, k + 2);
			}
			//System.out.println("Finished stripping comments.");
			
			
			//System.out.println("Extracting & parsing selectors...");
			Vector<Selector> selectors = new Vector<Selector>();
			n = 0;
			while ((k = sb.indexOf("}", n)) != -1) {
				try {
					selectors.addElement(new Selector(sb.substring(n, k + 1)));
				} catch (Exception e) {
					//System.out.println("  Error parsing selector: skipping...");
				}
				n = k + 1;
			}
			//System.out.println("Finished extracting & parsing selectors.");
			
			
			//System.out.println("Pretty-printing output...");
			
			for (Selector selector : selectors) {
				out.print(selector.toString());
			}
			out.print("\r\n");
			
			out.close();
			
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		
		
	}
}

class Selector {
	private Property[] properties;
	private String selector;
	
	/**
	 * Creates a new Selector using the supplied strings.
	 * @param selector The selector; for example, "div { border: solid 1px red; color: blue; }"
	 */
	public Selector(String selector) throws Exception {
		String[] parts = selector.split("\\{"); // We have to escape the { with a \ for the regex, which itself requires escaping for the string. Sigh.
		if (parts.length < 2) {
			throw new Exception("Warning: Incomplete selector: " + selector);
		}
		this.selector = parts[0].trim();
		String contents = parts[1].trim();
		if (contents.length() == 0) {
			throw new Exception("Warning: Empty selector body: " + selector);
		}
		if (contents.charAt(contents.length() - 1) != '}') { // Ensure that we have a leading and trailing brace.
			throw new Exception("Warning: Unterminated selector: " + selector);
		}
		contents = contents.substring(0, contents.length() - 2);
		this.properties = parseProperties(contents);
		sortProperties(this.properties);
	}
	
	/**
	 * Prints out this selector and its contents nicely, with the contents sorted alphabetically.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(this.selector).append("{");
		for (Property p : this.properties) {
			sb.append(p.toString());
		}
		sb.append("}");
		return sb.toString();
	}
	
	/**
	 * Parses out the properties of a selector's body.
	 * @param contents The body; for example, "border: solid 1px red; color: blue;"
	 */
	private Property[] parseProperties(String contents) {
		String[] parts = contents.split(";");
		Property[] results = new Property[parts.length];
		
		for (int i = 0; i < parts.length; i++) {
			try {
				results[i] = new Property(parts[i]);
			} catch (Exception e) {
				System.err.println(e.getMessage());
				results[i] = null;
			}
		}
		
		return results;
	}
	
	private void sortProperties(Property[] properties) {
		Arrays.sort(properties);
	}
}

class Property implements Comparable<Property> {
	protected String property;
	protected Part[] parts;
	
	/**
	 * Creates a new Property using the supplied strings. Parses out the values of the property selector.
	 * @param property The property; for example, "border: solid 1px red;" or "-moz-box-shadow: 3px 3px 3px rgba(255, 255, 0, 0.5);".
	 */
	public Property(String property) throws Exception {
		try {
			// Parse the property.
			String[] parts = property.split(":"); // Split "color: red" to ["color", " red"]
			if (parts.length < 2) {
				throw new Exception("Warning: Incomplete property: " + property);
			}
			this.property = parts[0].trim().toLowerCase();
			
			this.parts = parseValues(parts[1].trim().replaceAll(", ", ","));
			
		} catch (PatternSyntaxException e) {
			// Invalid regular expression used.
		}
	}
	
	/**
	 * Prints out this property nicely, with the contents sorted in a standardised order.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(this.property).append(":");
		for (Part p : this.parts) {
			sb.append(p.toString()).append(",");
		}
		sb.deleteCharAt(sb.length() - 1); // Delete the trailing comma.
		sb.append(";");
		return sb.toString();
	}
	
	public int compareTo(Property other) {
		return this.property.compareTo(other.property);
	}
	
	private Part[] parseValues(String contents) {
		String[] parts = contents.split(",");
		Part[] results = new Part[parts.length];
		
		for (int i = 0; i < parts.length; i++) {
			try {
				results[i] = new Part(parts[i]);
			} catch (Exception e) {
				System.err.println(e.getMessage());
				results[i] = null;
			}
		}
		
		return results;
	}
}

class Part {
	String contents;
	
	public Part(String contents) throws Exception {
		// Many of these regular expressions are adapted from those used in the YUI CSS Compressor.
		
		// For simpler regexes.
		this.contents = " " + contents;
		
		// Replace 0in, 0cm, etc. with just 0
		this.contents = this.contents.replaceAll("(\\s)(0)(px|em|%|in|cm|mm|pc|pt|ex)", "$1$2");
		
		// Replace 0.6 with .6
		this.contents = this.contents.replaceAll("(\\s)0+\\.(\\d+)", "$1.$2");
		
		this.contents = this.contents.trim();
		
		// Simplify multiple zeroes
		if (this.contents.equals("0 0 0 0")) this.contents = "0";
		if (this.contents.equals("0 0 0")) this.contents = "0";
		if (this.contents.equals("0 0")) this.contents = "0";
		
		//simplifyColours();
	}
	
	private void simplifyColours() {
		System.out.println("Simplifying colours; contents is " + this.contents);
		// Convert rgb() colours to Hex
		if (this.contents.toLowerCase().indexOf("rgb(") == 0) {
			String[] parts = this.contents.substring(4, this.contents.indexOf(")")).split(",");
			if (parts.length == 3) {
				int r = Integer.parseInt(parts[0], 10);
				int g = Integer.parseInt(parts[1], 10);
				int b = Integer.parseInt(parts[2], 10);
				
				StringBuffer sb = new StringBuffer();
				sb.append("#");
				if (r < 16) sb.append("0");
				sb.append(Integer.toHexString(r));
				if (g < 16) sb.append("0");
				sb.append(Integer.toHexString(g));
				if (b < 16) sb.append("0");
				sb.append(Integer.toHexString(b));
				
				this.contents = sb.toString();
			}
		}
		
		// Replace #223344 with #234
		if ((this.contents.indexOf("#") == 0) && (this.contents.length() == 7)) {
			this.contents = this.contents.toLowerCase(); // Always have hex colours in lower case.
			if ((this.contents.charAt(1) == this.contents.charAt(2)) &&
					(this.contents.charAt(3) == this.contents.charAt(4)) &&
					(this.contents.charAt(5) == this.contents.charAt(6))) {
				StringBuffer sb = new StringBuffer();
				sb.append("#").append(this.contents.charAt(1)).append(this.contents.charAt(3)).append(this.contents.charAt(5));
				this.contents = sb.toString();
			}
		}
	}
	
	public String toString() {
		return this.contents;
	}
}
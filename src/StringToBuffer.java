import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;


class StringToBuffer
{

	public static byte [] convertHexString (String pString, Integer pRequiredSize)
	{
		byte [] returnBytes = new byte[pRequiredSize];
		Arrays.fill(returnBytes, (byte)0x00);
		String strContent = pString.trim();

		// if content is hex string
		if(strContent.charAt(0) == '0' && strContent.charAt(1) == 'x')
		{
			// remove 0x
			String strBytes = strContent.substring(2);
			byte [] convertedBytes = new byte[strBytes.length()];

			// convert bytes
			for(int i = 0; i < strBytes.length(); i+=2)
			{
				convertedBytes[i >> 1] = (byte) ( (Character.digit(strBytes.charAt(i),   16) << 4) +
												   Character.digit(strBytes.charAt(i+1), 16)       );
			}

			// How many of these bytes can we keep? //
			Integer validLength;

			if(returnBytes.length >= convertedBytes.length)
				validLength = convertedBytes.length;
			else
				validLength = returnBytes.length;

			System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);
			
			
			String sssss = null;
			try {
				sssss = new String(returnBytes, "ASCII");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("m------m");
			System.out.println(sssss);
			System.out.println("^sssssed^");
		}
		
		return returnBytes;
	}
		
		
	public static byte [] convertLiteralString (String pString, Integer pRequiredSize)
	{
		byte [] returnBytes = new byte[pRequiredSize];
		Arrays.fill(returnBytes, (byte)0x00);
		String strContent = pString.trim();

		// if content is a string literal
		if(strContent.charAt(0) == '"' || strContent.charAt(0) == '\'')
		{
			// remove " or '
			String strSubContent = strContent.substring(1, strContent.length()-1).trim();
			byte [] convertedBytes = new byte[strSubContent.length()];

			// convert bytes
			for(int i = 0; i < strSubContent.length(); i++)
			{
				convertedBytes[i] = (byte) strSubContent.charAt(i);
			}

			// How many of these bytes can we keep? //
			Integer validLength;

			if(returnBytes.length >= convertedBytes.length)
				validLength = convertedBytes.length;
			else
				validLength = returnBytes.length;

			System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);

/*
						String sssss = null;
						try {
							sssss = new String(columnOfBytes, "ASCII");
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						System.out.println("v------v");
						System.out.println(sssss);
						System.out.println("^sssssed^");
						*/

		}
		
		return returnBytes;
	}
	
	
	
	
	
	public static byte [] convertNumericalString (String pString, Integer pRequiredSize)
	{
		byte [] returnBytes = new byte[pRequiredSize];
		Arrays.fill(returnBytes, (byte)0x00);
		String strContent = pString.trim();

		// if content is a string literal
		if(Character.isDigit(strContent.charAt(1)) && !isFloatingPointString(strContent))
		{
			// TODO: if size is 8 then make it a long-long.
			//		 else 4 is int
			//       anything_else make it raise an error
			Integer intContent = Integer.parseInt(strContent);
			byte [] convertedBytes = ByteBuffer.allocate(4).putInt(intContent).array(); // TODO: .order(ByteOrder.LITTLE_ENDIAN)

			// How many of these bytes can we keep? //
			Integer validLength;

			if(returnBytes.length >= convertedBytes.length)
				validLength = convertedBytes.length;
			else
				validLength = returnBytes.length;

			System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);

/*
			String sssss = null;
			try {
				sssss = new String(returnBytes, "ASCII");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("v------v");
			System.out.println(sssss);
			System.out.println("^sssssed^");
*/

		}
		
		return returnBytes;
	}
	
		

	
	public static byte [] convertFloatingPointString (String pString, Integer pRequiredSize)
	{
		byte [] returnBytes = new byte[pRequiredSize];
		Arrays.fill(returnBytes, (byte)0x00);
		String strContent = pString.trim();

		// if content is a string literal
		if(isFloatingPointString(strContent))
		{
			// TODO: if size is 8 then make it a double.
			//		 else 4 is float
			//       anything_else make it raise an error
			Float floatContent = Float.parseFloat(strContent);
			byte [] convertedBytes = ByteBuffer.allocate(4).putFloat(floatContent).array(); // TODO: .order(ByteOrder.LITTLE_ENDIAN)

			// How many of these bytes can we keep? //
			Integer validLength;

			if(returnBytes.length >= convertedBytes.length)
				validLength = convertedBytes.length;
			else
				validLength = returnBytes.length;

			System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);

/*
			String sssss = null;
			try {
				sssss = new String(returnBytes, "ASCII");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("v------v");
			System.out.println(sssss);
			System.out.println("^sssssed^");*/
		}
		
		return returnBytes;
	}
	


	private static boolean isFloatingPointString(String str) {
		try {
			Float.parseFloat(str);
			Double.parseDouble(str);
			// if it has a period, we count as floating point, else it is a whole number
			return (str.indexOf('.') >= 0) ? true : false;
		} catch (NumberFormatException e) {
			return false;
		}
	}

} // class //





































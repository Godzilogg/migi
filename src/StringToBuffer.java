import java.nio.ByteBuffer;
import java.util.Arrays;

class StringToBuffer
{
    public static byte [] convertHexString (String pString, Integer pRequiredSize)
    {
        byte [] returnBytes = new byte[pRequiredSize];
        Arrays.fill(returnBytes, (byte)0x00);
        String strContent = pString.trim();

        if(strContent.charAt(0) == '0' && strContent.charAt(1) == 'x')
        {
            // remove 0x
            String strBytes = strContent.substring(2);
            byte [] convertedBytes = new byte[strBytes.length()];
            Integer validLength;

            // convert bytes
            for(int i = 0; i < strBytes.length(); i+=2)
                convertedBytes[i >> 1] = (byte) ( (Character.digit(strBytes.charAt(i),   16) << 4) +
                        Character.digit(strBytes.charAt(i+1), 16)       );

            // How many of these bytes can we keep? //
            if(returnBytes.length >= convertedBytes.length)
                validLength = convertedBytes.length;
            else
                validLength = returnBytes.length;

            System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);

            putsDebug(returnBytes);
        }
        return returnBytes;
    }

    public static byte [] convertLiteralString (String pString, Integer pRequiredSize)
    {
        byte [] returnBytes = new byte[pRequiredSize];
        Arrays.fill(returnBytes, (byte)0x00);
        String strContent = pString.trim();

        if(strContent.charAt(0) == '"' || strContent.charAt(0) == '\'')
        {
            // remove " or '
            String strSubContent = strContent.substring(1, strContent.length()-1).trim();
            byte [] convertedBytes = new byte[strSubContent.length()];
            Integer validLength;

            // convert bytes
            for(int i = 0; i < strSubContent.length(); i++)
                convertedBytes[i] = (byte) strSubContent.charAt(i);

            // How many of these bytes can we keep? //
            if(returnBytes.length >= convertedBytes.length)
                validLength = convertedBytes.length;
            else
                validLength = returnBytes.length;

            System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);

            putsDebug(returnBytes);
        }
        return returnBytes;
    }

    public static byte [] convertNumericalString (String pString, Integer pRequiredSize)
    {
        byte [] returnBytes = new byte[pRequiredSize];
        Arrays.fill(returnBytes, (byte)0x00);
        String strContent = pString.trim();

        if(!isFloatingPointString(strContent))
        {
            byte [] convertedBytes;
            Integer validLength, copyFromOffset;

            if(pRequiredSize <= 4)
            {
                Integer intContent = Integer.parseInt(strContent);
                convertedBytes = ByteBuffer.allocate(4).putInt(intContent).array();
            }
            else
            {
                Long longContent = Long.parseLong(strContent);
                convertedBytes = ByteBuffer.allocate(8).putLong(longContent).array();
            }

            // How many of these bytes can we keep? //
            if(returnBytes.length >= convertedBytes.length)
            {
                validLength = convertedBytes.length;
                copyFromOffset = (returnBytes.length - convertedBytes.length);
            }
            else
            {
                validLength = returnBytes.length;
                copyFromOffset = (convertedBytes.length - returnBytes.length);
            }

            // Giving a `copyFromOffset` enables it to cut off the correct end of the bytes that
            // make up the integer in a little-endian system. Which means setting the size of the
            // column to 2 would yield a word or 'short' value integer. A column size of 1 would yield a byte.
            //
            // Example: 00 00 00 03 == base 10 number 3
            // System.arraycopy(convertedBytes, 0, returnBytes, 0, 3);
            // Output bytes: 00 00 00 == base 10 number 0
            //
            // Example: 00 00 00 03 == base 10 number 3
            // System.arraycopy(convertedBytes, 1, returnBytes, 0, 3);
            // Output bytes: 00 00 03 == base 10 number 3
            //
            if(pRequiredSize <= 8)
                System.arraycopy(convertedBytes, copyFromOffset, returnBytes, 0, validLength);
            else
                System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);

            putsDebug(returnBytes);
        }
        return returnBytes;
    }

    public static byte [] convertFloatingPointString (String pString, Integer pRequiredSize)
    {
        byte [] returnBytes = new byte[pRequiredSize];
        Arrays.fill(returnBytes, (byte)0x00);
        String strContent = pString.trim();

        if(isFloatingPointString(strContent))
        {
            Integer validLength;
            byte [] convertedBytes;

            if(pRequiredSize <= 4)
            {
                Float floatContent = Float.parseFloat(strContent);
                convertedBytes = ByteBuffer.allocate(4).putFloat(floatContent).array();
            }
            else
            {
                Double doubleContent = Double.parseDouble(strContent);
                convertedBytes = ByteBuffer.allocate(8).putDouble(doubleContent).array();
            }

            // How many of these bytes can we keep? //
            if(returnBytes.length >= convertedBytes.length)
                validLength = convertedBytes.length;
            else
                validLength = returnBytes.length;

            System.arraycopy(convertedBytes, 0, returnBytes, 0, validLength);

            putsDebug(returnBytes);
        }
        return returnBytes;
    }

    public static boolean isFloatingPointString(String str)
    {
        try
        {
            Float.parseFloat(str);
            Double.parseDouble(str);

            // if it has a period, we count as floating point, else it is a whole number
            return (str.indexOf('.') >= 0) ? true : false;
        }
        catch (NumberFormatException e) { return false; }
    }

    private static void putsDebug(byte [] debugBytes)
    {
/*
		String superDebuggerString = null;

		try
		{
			superDebuggerString = new String(debugBytes, "ASCII");
		}
		catch (UnsupportedEncodingException e) { e.printStackTrace(); }

		System.out.println("v-----------------v");
		System.out.println("|  " + superDebuggerString);
		System.out.println("^-----------------^");*/
    }

} // class //

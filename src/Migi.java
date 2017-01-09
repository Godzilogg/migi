import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

class Migi
{
	static String mFilename = "";
	static String mMigrationPath = "";
	static String [] mArgs;
	static byte [] mFileBytes;
	static String mFileID = "RAD";
	static Integer mFileIDVersion = 0;
	static Document docXML;

	private static HashMap<Integer, String> mXMLCurrentMigrationColumnSizes = new HashMap<Integer, String>();
	private static HashMap<String, Integer> mXMLCurrentMigrationColumnIDs = new HashMap<String, Integer>();
	private static HashMap<String, Integer> mXMLCurrentMigrationColumnIndices = new HashMap<String, Integer>();
	
	private static HashMap<Integer,byte[]> mCurrentBufferData = new HashMap<Integer, byte []>();
	private static HashMap<Integer,String> mCurrentBufferColumnIDs = new HashMap<Integer, String>();
	private static HashMap<Integer,Integer> mCurrentBufferColumnSizes = new HashMap<Integer, Integer>();

	private static HashMap<Integer,byte[]> mNewBufferData = new HashMap<Integer, byte []>();
	private static HashMap<Integer,String> mNewBufferColumnIDs = new HashMap<Integer, String>();
	private static HashMap<Integer,Integer> mNewBufferColumnSizes = new HashMap<Integer, Integer>();

	private static HashMap<Integer, String> mXMLNextMigrationColumnSizes = new HashMap<Integer, String>();
	private static HashMap<String, Integer> mXMLNextMigrationColumnIDs = new HashMap<String, Integer>();
	private static HashMap<String, Integer> mXMLNextMigrationColumnIndices = new HashMap<String, Integer>();

	public static final Integer DEFAULT_HEADER_SIZE = 8;
	public static final Integer DEFAULT_HEADER_SIZE_INDEX = 1;
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String HEADER_ID = "!!!___HEADER___!!!"; // TODO: ensure no col id matches this.
	
	
	
	
	
	
	
	
	
	
	/// TODO: remove all (no size change)
	
	
	
	
	
	

	// main ( String [] args )
	//
	// The heartbeat of the application, derp, <A>FWFA><#@M4tlkq34lakj lkadslkfja;sldkjfl;awj4e <--- (I can only get away with this on personal projects.)
	//
	// To run open up your terminal and cd to the Migi.class directory
	// and type: java Migi -i "file.fun.bin" -m "C:\MyMigrations\FunMigration.xml"
	//
	public static void main ( String [] args )
	{
		// TODO create more instances of Migi here:
		// Migi.new and search through directories to get all migrations done in one command.
		
		if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN))
			migiComplainAndExit("Error - System is big endian");

		mArgs = args;

		if(args.length <= 0) { migiComplainAndExit(migiHelperMessage()); }
		if(!Arrays.asList(args).contains("-i")) { migiComplainAndExit("-i not specified."); }
		if(!Arrays.asList(args).contains("-m")) { migiComplainAndExit("-m not specified."); }

		for(int i = 0; i < args.length; i++)
		{
			if(new String("-h").equals(args[i]) ||
					new String("--h").equals(args[i]) ||
					new String("-help").equals(args[i]) ||
					new String("--help").equals(args[i]) ||
					new String("-godhelpme").equals(args[i]) ||
					new String("--godhelpme").equals(args[i]) )
			{
				migiComplainAndExit(migiHelperMessage());
			}
		}

		System.out.println(ANSI_GREEN + "--- Welcome to migi 29.0.a ---" + ANSI_RESET);

		try
		{
			getFilePaths();

			processBinFile();
			processMigration();

			migrateForward();
		}
		catch (ParserConfigurationException e) { e.printStackTrace(); }
		catch (SAXException e) { e.printStackTrace(); }
		catch (FileNotFoundException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }
		finally {
			// TODO: we may not have to actually cleanup anything..
			// cleanupFiles();
		}

		migiComplainAndExit("");
	}


	// migrateForward ()
	//
	// Creates a new binary file based on the next migrations specifications.
	//
	private static void migrateForward ()
	{
		// Get all migrations for file.
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
		Integer currentFileMigrationVersion = mFileIDVersion;
		Integer latestXMLMigrationVersion = nListMigrations.getLength();
		Integer nextFileMigrationVersion = currentFileMigrationVersion +1;
		
		if(currentFileMigrationVersion >= latestXMLMigrationVersion)
			migiComplainAndExit("Aborting - Bin file is up to date with latest migration for: " + mFileID + " v" + currentFileMigrationVersion);
		
		calcNextMigrationColumnAttrs(nextFileMigrationVersion);
		copyCurrentHeaderBufferIntoNextHeaderBuffer();
		
		System.out.println("v--------ruuning migration   " + (currentFileMigrationVersion));
		System.out.println("v----------to  " + (latestXMLMigrationVersion));

		// Start at current version and migrate forwards
		for(int m = currentFileMigrationVersion; m < (latestXMLMigrationVersion-1); ++m)
		{
			System.out.println("Running Migration: #" + (m+1));
			
			Node nNextMigration = nListMigrations.item(m+1);
			NodeList nNextMigrationListColumns = ((Element)nNextMigration).getElementsByTagName("col");
			
			calcCurrentMigrationColumnIDs(m);
			calcNextMigrationColumnIDs(m+1);
			
			for(int c = 0; c < nNextMigrationListColumns.getLength(); c++)
			{
				String nextMigrationColumnSize = calcColumnSizeFromNode(nNextMigrationListColumns.item(c), (m+1));
				String nextMigrationColumnID   = calcColumnIDFromNode(nNextMigrationListColumns.item(c), (m+1), c);
				
				System.out.println("==   " + nextMigrationColumnID);
				
				// if column exists in previous migration
				if( (mXMLCurrentMigrationColumnIDs.containsKey(nextMigrationColumnID)) )
				{
					
					System.out.println("<<<< " + nextMigrationColumnID);

					// get previous migration column and all data associated.
					Integer currentMigrationColumnIndex = mXMLCurrentMigrationColumnIndices.get(nextMigrationColumnID);
					Integer currentMigrationColumnSize  = mCurrentBufferColumnSizes.get(currentMigrationColumnIndex);
					// String  currentMigrationColumnID    = mCurrentBufferColumnIDs.get(currentMigrationColumnIndex);
					
					//-----------------------------------------------------------
					// has the column size changed
					//-----------------------------------------------------------
					Integer latestColumnSize;
					Integer copySize;
					
					// has size changed?
					if( currentMigrationColumnSize != Integer.parseInt(nextMigrationColumnSize) )
						latestColumnSize = Integer.parseInt(nextMigrationColumnSize);
					else
						latestColumnSize = currentMigrationColumnSize;
					
					// allocate memory with the new size (if anything has changed)
					byte [] nextColumnOfBytes = new byte[latestColumnSize];
					Arrays.fill(nextColumnOfBytes, (byte)0x00);
					
					// TODO: asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf
					// We are not getting proper index here.
					// What if mCurrentBufferData[migration = 0][column = 2+DEFAULT_HEADER] == "abc"
					// What if mCurrentBufferData[migration = 1][column = 2+DEFAULT_HEADER] == "iii"
					//
					// mCurrentBufferData.get(currentMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX);
					//
					// In other words, this means saving data like we save Size.
					//
					// "asdf" => "abc"
					// "asdf"
					// "asdf"
					//
					// They need to be stored in another hash. like:
					// mHangOverBufferDataByColumnID<String, byte []> // ColumnID by ByteArray, will fix everything..
					// because now we are overwriting mCurrentBufferData index data..
					// so we loose it.
					byte [] currentColumnOfBytes = mCurrentBufferData.get(currentMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX);
					
					
					byte [] newColumnOfBytes = mNewBufferData.get(currentMigrationColumnIndex);
					System.out.println("<<<< latestColumnSize  " + latestColumnSize);
					System.out.println("<<<< nextColumnOfBytes.length  " + nextColumnOfBytes.length);
					System.out.println("<<<< currentColumnOfBytes.length  " + currentColumnOfBytes.length);
					
					if(newColumnOfBytes != null)
					System.out.println("<<<< newColumnOfBytes.length  " + newColumnOfBytes.length);
					
					
					
					if( nextColumnOfBytes.length >= currentColumnOfBytes.length )
						copySize = currentColumnOfBytes.length;
					else
						copySize = nextColumnOfBytes.length;
					
					System.arraycopy(currentColumnOfBytes, 0, nextColumnOfBytes, 0, copySize);

					mNewBufferData.put((c+DEFAULT_HEADER_SIZE_INDEX), nextColumnOfBytes);
					mNewBufferColumnIDs.put((c+DEFAULT_HEADER_SIZE_INDEX), nextMigrationColumnID);
					mNewBufferColumnSizes.put((c+DEFAULT_HEADER_SIZE_INDEX), latestColumnSize);
					//-----------------------------------------------------------
					// end
					//-----------------------------------------------------------
					
					
					//-----------------------------------------------------------
					// has the column been given a value
					//-----------------------------------------------------------
					Integer nextMigrationColumnSizeInt = latestColumnSize;
					byte [] columnOfBytes = new byte[nextMigrationColumnSizeInt];
					
					// get <col> !this content! </col>
					String strContent = nNextMigrationListColumns.item(c).getTextContent().trim();

					System.out.println("<<<<< [columnID2] " + nextMigrationColumnID);
					
					// Has the new column been given a default value?
					if(strContent == null || strContent.isEmpty())
					{
						System.out.println("<<<<< [abyss] " );
						
						// TODO: asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf
						// We are not getting proper index here.
						// What if mCurrentBufferData[migration = 0][column = 2+DEFAULT_HEADER] == "abc"
						// What if mCurrentBufferData[migration = 1][column = 2+DEFAULT_HEADER] == "iii"
						//
						// mCurrentBufferData.get(currentMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX);
						byte [] defaultBytes = mCurrentBufferData.get(currentMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX);
						
						if( columnOfBytes.length >= defaultBytes.length )
							copySize = defaultBytes.length;
						else
							copySize = columnOfBytes.length;
						
						System.arraycopy(defaultBytes, 0, columnOfBytes, 0, copySize);
					}
					else
					{
						// if content is hex string
						if(strContent.charAt(0) == '0' && strContent.charAt(1) == 'x')
						{
							columnOfBytes = StringToBuffer.convertHexString(strContent, nextMigrationColumnSizeInt);
						}
						// if content is a string literal
						else if(strContent.charAt(0) == '"' || strContent.charAt(0) == '\'')
						{
							columnOfBytes = StringToBuffer.convertLiteralString(strContent, nextMigrationColumnSizeInt);
							
							String superDebuggerString = null;
							try { superDebuggerString = new String(columnOfBytes, "ASCII"); }
							
							catch (UnsupportedEncodingException e) { e.printStackTrace(); }
							System.out.println("<<<<< [columnID] " + nextMigrationColumnID);
							System.out.println("<<<<< [columnsuperDebuggerString] " + superDebuggerString);
							
						}
						// if content is a numerical string
						else if(Character.isDigit(strContent.charAt(1)) && !StringToBuffer.isFloatingPointString(strContent))
						{
							columnOfBytes = StringToBuffer.convertNumericalString(strContent, nextMigrationColumnSizeInt);
						}
						// if content is a floating point numerical string
						else if(StringToBuffer.isFloatingPointString(strContent))
						{
							columnOfBytes = StringToBuffer.convertFloatingPointString(strContent, nextMigrationColumnSizeInt);
						}
						
						mNewBufferData.put((c+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
					}

					mNewBufferColumnIDs.put((c+DEFAULT_HEADER_SIZE_INDEX), nextMigrationColumnID);
					mNewBufferColumnSizes.put((c+DEFAULT_HEADER_SIZE_INDEX), nextMigrationColumnSizeInt);
					//-----------------------------------------------------------
					// end
					//-----------------------------------------------------------
				} 
				else // this is a new column
				{

					// Demand that we require a column size for new columns introduced in the schema.
					if( new String("(no size change)").equals(nextMigrationColumnSize) )
						migiComplainAndExit("Error - Cannot create new column <col> tag without 'size=' attribute.\nProblem migration: " + m + "\nProblem column: " + (c+1) + "\nProblem id: " + nextMigrationColumnID);

					//-----------------------------------------------------------
					// has the column been given a value
					//-----------------------------------------------------------
					Integer nextMigrationColumnSizeInt = Integer.parseInt(nextMigrationColumnSize);
					byte [] columnOfBytes = new byte[nextMigrationColumnSizeInt];
					
					// get <col> !this content! </col>
					String strContent = nNextMigrationListColumns.item(c).getTextContent().trim();

					// Has the new column been given a default value?
					if(strContent == null || strContent.isEmpty())
					{
						Arrays.fill(columnOfBytes, (byte)0x00);
					}
					else
					{
						// if content is hex string
						if(strContent.charAt(0) == '0' && strContent.charAt(1) == 'x')
						{
							columnOfBytes = StringToBuffer.convertHexString(strContent, nextMigrationColumnSizeInt);
						}
						// if content is a string literal
						else if(strContent.charAt(0) == '"' || strContent.charAt(0) == '\'')
						{
							columnOfBytes = StringToBuffer.convertLiteralString(strContent, nextMigrationColumnSizeInt);
						}
						// if content is a numerical string
						else if(Character.isDigit(strContent.charAt(1)) && !StringToBuffer.isFloatingPointString(strContent))
						{
							columnOfBytes = StringToBuffer.convertNumericalString(strContent, nextMigrationColumnSizeInt);
						}
						// if content is a floating point numerical string
						else if(StringToBuffer.isFloatingPointString(strContent))
						{
							columnOfBytes = StringToBuffer.convertFloatingPointString(strContent, nextMigrationColumnSizeInt);
						}
					}

					mNewBufferData.put((c+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
					mNewBufferColumnIDs.put((c+DEFAULT_HEADER_SIZE_INDEX), nextMigrationColumnID);
					mNewBufferColumnSizes.put((c+DEFAULT_HEADER_SIZE_INDEX), nextMigrationColumnSizeInt);
					//-----------------------------------------------------------
					// end
					//-----------------------------------------------------------
				}
				
				String superDebuggerString = null;
				try
				{
					superDebuggerString = new String(mNewBufferData.get(c+1), "ASCII");
				}
				catch (UnsupportedEncodingException e) { e.printStackTrace(); }

		    	System.out.println("m-----------------------");
		    	System.out.println("mi|   " + mNewBufferColumnIDs.get(c+1));
		    	System.out.println("ms|   " + mNewBufferColumnSizes.get(c+1));
		    	System.out.println("md|   " + superDebuggerString);
		    	System.out.println("mds|   " + superDebuggerString.length());
		    	System.out.println("m-----------------------");

			} // for(c)
			
			// Very important! Keep this line or die miserableleyl
			copyNextBufferIntoCurrentBuffer();
			
		} // for(m)
		
		saveToFile(mFilename + ".mig.bin");
	}
	
	
	private static void saveToFile (String pPath)
	{
		FileOutputStream fileOuput = null;
		
		try
		{
			fileOuput = new FileOutputStream(pPath);
		    
		    for(int i = 0; i<mNewBufferData.size(); ++i) {
		    	fileOuput.write(mNewBufferData.get(i) );
		    	
		    	
				String superDebuggerString = null;

				try
				{
					superDebuggerString = new String(mNewBufferData.get(i), "ASCII");
				}
				catch (UnsupportedEncodingException e) { e.printStackTrace(); }

		    	System.out.println("v-------------------------");
		    	System.out.println("v--i|   " + mNewBufferColumnIDs.get(i));
		    	System.out.println("v--s|   " + mNewBufferColumnSizes.get(i));
		    	System.out.println("v--d|   " + superDebuggerString);
		    	System.out.println("v-------------------------");

		    }
		    System.out.println("Done");

		}
		catch (IOException e) { e.printStackTrace(); }
		finally
		{
			try
			{
				if (fileOuput != null)
					fileOuput.close();
			}
			catch (IOException ex) { ex.printStackTrace(); }
		}
	}
	
	
	
	//=======================================
	//
	//=======================================
	private static void calcCurrentMigrationColumnIDs (Integer pMigration)
	{
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);

		//for(int m = 0; m < nListMigrations.getLength(); m++)
		{
			Node nMigration = nListMigrations.item(pMigration);
			NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

			for(int c = 0; c < columnList.getLength(); c++)
			{
				System.out.println("v-------------------------presouls " );
				
				String columnID = calcColumnIDFromNode(columnList.item(c), pMigration, c);
				String columnSize = calcColumnSizeFromNode(columnList.item(c), pMigration);
		    	System.out.println("v-------------------------presoulsID " + columnID);
		    	System.out.println("v-------------------------presoulsSize " + columnSize);
		    	
				mXMLCurrentMigrationColumnSizes.put(c, columnSize);
				mXMLCurrentMigrationColumnIDs.put(columnID, pMigration);
				mXMLCurrentMigrationColumnIndices.put(columnID, c);
			}
		}
	}
	
	private static void calcNextMigrationColumnIDs (Integer pMigration)
	{
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
		
		//for(int m = 0; m < nListMigrations.getLength(); m++)
		{
			Node nMigration = nListMigrations.item(pMigration);
			NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

			for(int c = 0; c < columnList.getLength(); c++)
			{

				String columnID = calcColumnIDFromNode(columnList.item(c), pMigration, c);
				String columnSize = calcColumnSizeFromNode(columnList.item(c), pMigration);
		    	System.out.println("v-------------------------soulID " + columnID);
		    	System.out.println("v-------------------------soulSize " + columnSize);
		    	
				mXMLNextMigrationColumnSizes.put(c, columnSize);
				mXMLNextMigrationColumnIDs.put(columnID, pMigration);
				mXMLNextMigrationColumnIndices.put(columnID, c);
			}
		}
	}
	//=======================================
	//
	//=======================================
	
	
	
	
	
	
	// copyCurrentHeaderBufferIntoNextHeaderBuffer
	//
	// Header information is assumed to be stored in the first segment of the enumerable.
	//
	private static void copyCurrentHeaderBufferIntoNextHeaderBuffer ()
	{
		mNewBufferData.put(0, mCurrentBufferData.get(0));
		mNewBufferColumnIDs.put(0, mCurrentBufferColumnIDs.get(0));
		mNewBufferColumnSizes.put(0, mCurrentBufferColumnSizes.get(0));
	}
	
	private static void copyNextBufferIntoCurrentBuffer ()
	{
		for(int i = 0; i<mNewBufferData.size(); ++i)
		{
			mCurrentBufferData.put(i, mNewBufferData.get(i));
			mCurrentBufferColumnIDs.put(i, mNewBufferColumnIDs.get(i));
			mCurrentBufferColumnSizes.put(i, mNewBufferColumnSizes.get(i));
		}
	}
	
	private static void processBinFile () throws IOException
	{
		Path path = Paths.get(mFilename);
		mFileBytes = Files.readAllBytes(path);

		// Get fileID from bin file and save it for XML processing later.
		byte [] bytesFileID = Arrays.copyOfRange(mFileBytes, 0, 3);
		mFileID = new String(bytesFileID, "ASCII");
		mFileIDVersion = (int)mFileBytes[3];
	}

	private static void processMigration () throws SAXException, ParserConfigurationException, IOException
	{
		File fXmlFile = new File(mMigrationPath);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		docXML = dBuilder.parse(fXmlFile);

		// Ensures: <tag>hello
		// world</tag>
		// Isn't separated into:
		// text:  hello
		// text2: world
		// but Normalizes it to:
		// text: hello world
		docXML.getDocumentElement().normalize();

		// Get root element of XML file
		String rootElement = docXML.getDocumentElement().getNodeName();

		if( !(new String("migration").equals(rootElement)) )
			migiComplainAndExit("Failure - Improper Migration File: XML root element must be '<migration>' tag.\nGot  '<" + rootElement + ">'  instead.");

		// Get all Migrations for file.
		NodeList nList = docXML.getElementsByTagName(mFileID);

		if(nList.getLength() <= 0)
			migiComplainAndExit("Exiting - No migrations found for '" + mFileID + "'");

		if(nList.getLength() == mFileIDVersion)
			migiComplainAndExit("Exiting - File is up to date with latest Migration v" + mFileIDVersion);
		
		validateXML();

		divideFileByCurrentMigration();
	}
	
	// validateXML ()
	//
	// Ensures XML file does not contains unique column IDs per migration.
	// TODO: Add other validations here
	//
	private static void validateXML ()
	{
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);

		for(int m = 0; m < nListMigrations.getLength(); m++)
		{
			Node nMigration = nListMigrations.item(m);
			NodeList columnList = ((Element) nMigration).getElementsByTagName("col");
			HashMap<String, Integer> columnIDs = new HashMap<String, Integer>();

			for(int c = 0; c < columnList.getLength(); c++)
			{
				String columnID = calcColumnIDFromNode(columnList.item(c), m, c);
				
				if(columnIDs.get(columnID) == null)
					columnIDs.put(columnID, c);
				else
					migiComplainAndExit("Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );
			}
		}
	}

	// divideFileByCurrentMigration ()
	//
	// Take file's binary data and sub-divide into columns related to the
	// XML DB schema.
	//
	private static void divideFileByCurrentMigration ()
	{
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
		Node nCurrentMigration = nListMigrations.item(mFileIDVersion);
		NodeList listColumns = ((Element)nCurrentMigration).getElementsByTagName("col");

		calcMigrationColumnAttrs(mFileIDVersion);
		chunkBinFile(listColumns);
	}

	// calcMigrationColumnAttrs ()
	//
	// Runs through all migrations up to the pCurrentMigration to get sizes, ids, etc.. for all columns.
	// It only update sequential sizes if a new size is specified in a following migration.
	//
	// First migration requires size attributes for all column tags, or else the end of the world will occur.
	//
	private static void calcMigrationColumnAttrs (Integer pCurrentMigration)
	{
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
		int stopMigrationIndex = (pCurrentMigration > nListMigrations.getLength()) ? nListMigrations.getLength() : pCurrentMigration;

		for(int m = 0; m < stopMigrationIndex; m++)
		{
			Node nMigration = nListMigrations.item(m);
			NodeList columnList = ((Element) nMigration).getElementsByTagName("col");
			
			for(int c = 0; c < columnList.getLength(); c++)
			{
				String columnSize = calcColumnSizeFromNode(columnList.item(c), pCurrentMigration);
				String columnID   = calcColumnIDFromNode(columnList.item(c), m, c);

				if(mXMLCurrentMigrationColumnIDs.containsKey(columnID))
					if(mXMLCurrentMigrationColumnIDs.get(columnID) == m)
						migiComplainAndExit("Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );

				// Log the unique columnIDs and which migration version they support.
				mXMLCurrentMigrationColumnIDs.put(columnID, m);
				mXMLCurrentMigrationColumnIndices.put(columnID, c);

				// Only update sequential sizes if a new size is specified in the migration.
				if( !(new String("(no size change)").equals(columnSize)) )
					mXMLCurrentMigrationColumnSizes.put(c, columnSize);
				else if(m == 0) // First migration requires size attributes for all column tags
					migiComplainAndExit("Error - First migration contains a <col> tag missing a 'size=' attribute.\nProblem column: " + (c+1) );
			}
		}
	}

	private static void calcNextMigrationColumnAttrs (Integer pMigration)
	{
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
		Node nMigration = nListMigrations.item(pMigration);
		NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

		for(int c = 0; c < columnList.getLength(); c++)
		{
			String columnSize = calcColumnSizeFromNode(columnList.item(c), pMigration);
			String columnID   = calcColumnIDFromNode(columnList.item(c), pMigration, c);
			Integer columnIndex = c;
			
			if(mXMLCurrentMigrationColumnIDs.containsKey(columnID))
				if(mXMLCurrentMigrationColumnIDs.get(columnID) == pMigration)
					migiComplainAndExit("Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (pMigration+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );

			mXMLNextMigrationColumnIndices.put(columnID, columnIndex);
			mXMLNextMigrationColumnIDs.put(columnID, pMigration);

			// Only update sequential sizes if a new size is specified in the migration.
			if( !(new String("(no size change)").equals(columnSize) ) )
				mXMLNextMigrationColumnSizes.put(c, columnSize);
		}
	}

	private static void chunkBinFile (NodeList pCurrentListColumns)
	{
		Integer offset = initCurrentBufferHeader();

		for(int i = 0; i < pCurrentListColumns.getLength(); i++)
		{
			String columnID = calcColumnIDFromNode(pCurrentListColumns.item(i), mFileIDVersion, i);
			String columnSize = calcColumnSizeFromNode(pCurrentListColumns.item(i), mFileIDVersion);
			Integer latestColumnSize = Integer.parseInt(columnSize);
			
			System.out.println("v-----------------v migration    " + mFileIDVersion );
			System.out.println("v-----------------v columnID    " + columnID );
			System.out.println("v-----------------v columnSize  " + columnSize );
			System.out.println("v-----------------v wtf  " + mXMLCurrentMigrationColumnSizes.get(i));

			byte [] columnOfBytes = new byte[latestColumnSize];
			System.arraycopy(mFileBytes, offset, columnOfBytes, 0, latestColumnSize);

			mCurrentBufferData.put((i+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
			mCurrentBufferColumnIDs.put((i+DEFAULT_HEADER_SIZE_INDEX), columnID);
			mCurrentBufferColumnSizes.put((i+DEFAULT_HEADER_SIZE_INDEX), latestColumnSize);

			offset += latestColumnSize;
		}
	}

	private static String calcColumnSizeFromNode (Node pColumn, int pMigrationNumber)
	{
		NamedNodeMap mapAttrs = pColumn.getAttributes();
		Node nColumnSize = mapAttrs.getNamedItem("size");
		String columnID = calcColumnIDFromNode (pColumn, pMigrationNumber, 0);
		String columnSize = null;
		
		if(nColumnSize != null)
			return nColumnSize.getTextContent().trim();
		
		System.out.println("v-----------------v wwww  " + columnID);
		
		// search through all the migrations and log the latest size for the specific columnID
		// up to the current migration
		
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);

		for(int m = 0; m < pMigrationNumber; m++)
		{
			System.out.println("v-----------------v fffff  " + m);
			Node nMigration = nListMigrations.item(m);
			NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

			for(int c = 0; c < columnList.getLength(); c++)
			{
				Node nextColumn = columnList.item(c);
				String nextColumnID = calcColumnIDFromNode(columnList.item(c), m, c);
				String nextColumnSize = columnSizeFromNode(nextColumn);
				
				if( (new String(columnID).equals(nextColumnID) ) && nextColumnSize != null )
					columnSize = nextColumnSize;
			}
		}
		
		if(columnSize == null)
			migiComplainAndExit("Error - Column <col> ID: " + columnID + " was never assigned a size= attribute in any migration.");
		
		System.out.println("v-----------------v fffff=== " + columnID);
		System.out.println("v-----------------v fffff=== " + columnSize);
		
		return columnSize;
	}
	
	private static String columnSizeFromNode (Node pColumn)
	{
		NamedNodeMap ColumnAttrs = pColumn.getAttributes();
		Node ColumnSize = ColumnAttrs.getNamedItem("size");
		return (ColumnSize == null) ? null : ColumnSize.getTextContent().trim();
	}

	private static String calcColumnIDFromNode (Node pColumn, int pMigrationNumber, int pColumnNumber)
	{
		NamedNodeMap mapAttrs = pColumn.getAttributes();
		Node nColumnID = mapAttrs.getNamedItem("id");

		if(nColumnID == null)
			migiComplainAndExit("Error - Column <col> tag missing a 'id=' attribute." + "\nProblem migration: " + (pMigrationNumber+1) + "\nProblem column: " + (pColumnNumber+1) );

		return nColumnID.getTextContent();
	}

	private static Integer initCurrentBufferHeader ()
	{
		mCurrentBufferData.clear();

		byte [] bytesFileID = Arrays.copyOfRange(mFileBytes, 0, DEFAULT_HEADER_SIZE);
		mCurrentBufferData.put(0, bytesFileID);
		mCurrentBufferColumnIDs.put(0, HEADER_ID);
		mCurrentBufferColumnSizes.put(0, DEFAULT_HEADER_SIZE);

		return DEFAULT_HEADER_SIZE;
	}

	private static void migiComplainAndExit ( String complaint ) {
		System.out.println(complaint);
		System.exit(0);
	}

	private static String migiHelperMessage () {
		return "Usage and commands: \n"
				+ "migi -i input_file -m migration_directory \n"
				+ "migi -i input_file -m migration_file \n";
	}
	
	public static Object getHashMapKeyFromValue(HashMap hm, Object value)
	{
		for(Object o : hm.keySet())
			if (hm.get(o).equals(value))
				return o;
		
		return null;
	}

	private static void getFilePaths ()
	{
		for(int i = 0; i < mArgs.length; i++)
		{
			System.out.print(mArgs[i] + " ");

			if(new String("-i").equals(mArgs[i]))
				if(mArgs.length <= i+1)
					migiComplainAndExit("-i specified with no input path specified.");
				else
					mFilename = mArgs[i+1];

			if(new String("-m").equals(mArgs[i]))
				if(mArgs.length <= i+1)
					migiComplainAndExit("-m specified with no migration path or file specified.");
				else
					mMigrationPath = mArgs[i+1];
		}
		System.out.print("\n");
	}

} // class Migi //



































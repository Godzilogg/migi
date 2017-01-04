import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;
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
	
	private static HashMap<String, Integer> mListOfHandledColumnIDs = new HashMap<String, Integer>();

	public static final Integer DEFAULT_HEADER_SIZE = 8;
	public static final Integer DEFAULT_HEADER_SIZE_INDEX = 1;
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String HEADER_ID = "!!!___HEADER___!!!"; // TODO: ensure no col id matches this.

	public static void main ( String [] args )
	{

		// TODO create more instances of Migi here:
		// Migi.new and search through directories to get all migrations done in one command.

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
			// cleanupFiles();
		}

		migiComplainAndExit("");
	}




	private static void migrateForward ()
	{
		// Get all migrations for file.
		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
		Integer currentFileMigrationVersion = mFileIDVersion;
		Integer latestXMLMigrationVersion = nListMigrations.getLength() +1;

		if(currentFileMigrationVersion >= latestXMLMigrationVersion)
			return;
		
		Integer nextFileMigrationVersion = currentFileMigrationVersion+1;
		calcNextMigrationColumnAttrs(nextFileMigrationVersion);
		
		copyCurrentHeaderBufferIntoNextHeaderBuffer();

		// Start at current version and migrate forwards
		for(int m = currentFileMigrationVersion-1; m < (latestXMLMigrationVersion-1); ++m)
		{
			System.out.println("Running Migration: v" + (m+1));
			/*
			Node nCurrentMigration = nListMigrations.item(currentFileMigrationVersion);
			NodeList nCurrentMigrationListColumns = ((Element)nCurrentMigration).getElementsByTagName("col");
			*/
			Node nNextMigration = nListMigrations.item(nextFileMigrationVersion);
			NodeList nNextMigrationListColumns = ((Element)nNextMigration).getElementsByTagName("col");
			
			for(int nextMigrationColumnIndex = 0; nextMigrationColumnIndex < nNextMigrationListColumns.getLength(); nextMigrationColumnIndex++)
			{
				String nextMigrationColumnSize = calcColumnSizeFromNode(nNextMigrationListColumns.item(nextMigrationColumnIndex));
				String nextMigrationColumnID   = calcColumnIDFromNode(nNextMigrationListColumns.item(nextMigrationColumnIndex), m, nextMigrationColumnIndex);
				
				// if column exists in previous migration
				if( (mXMLCurrentMigrationColumnIDs.containsValue(nextMigrationColumnID)) )
				{
					// get previous migration column and all data associated.
					Integer currentMigrationColumnIndex = mXMLCurrentMigrationColumnIndices.get(nextMigrationColumnID);
					Integer currentMigrationColumnSize  = mCurrentBufferColumnSizes.get(currentMigrationColumnIndex);
					String  currentMigrationColumnID    = mCurrentBufferColumnIDs.get(currentMigrationColumnIndex);
					
					//-----------------------------------------------------------
					// Handling size changes to column // aka has size changed?
					//-----------------------------------------------------------
					Integer latestColumnSize;
					
					// has size changed?
					if( currentMigrationColumnSize != Integer.parseInt(nextMigrationColumnSize) )
						latestColumnSize = Integer.parseInt(nextMigrationColumnSize);
					else
						latestColumnSize = currentMigrationColumnSize;
					
					// allocate memory with the new size (if anything has changed)
					byte [] nextColumnOfBytes = new byte[latestColumnSize];
					byte [] currentColumnOfBytes = mCurrentBufferData.get(currentMigrationColumnIndex);
					System.arraycopy(currentColumnOfBytes, 0, nextColumnOfBytes, 0, latestColumnSize);

					mNewBufferData.put((nextMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX), nextColumnOfBytes);
					//-----------------------------------------------------------
					// end
					//-----------------------------------------------------------
					
					
					//-----------------------------------------------------------
					// has the file been 
					//-----------------------------------------------------------
					
					// todo
					
					//-----------------------------------------------------------
					// end
					//-----------------------------------------------------------
					
					
					mNewBufferColumnIDs.put((nextMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX), columnID);
					mNewBufferColumnSizes.put((nextMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX), latestColumnSize);
					
					
					byte [] columnOfBytes = new byte[maxSizeOfBytes];
					
					mNewBufferData.put((c+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
					mNewBufferColumnIDs.put((c+DEFAULT_HEADER_SIZE_INDEX), nextMigrationColumnID);
					mNewBufferColumnSizes.put((c+DEFAULT_HEADER_SIZE_INDEX), Integer.parseInt(nextMigrationColumnSize));
						
					// is it modified?
					// Check Size Changes
					// Check Values passed in. -> Convert Values if Necessary
					
					// is it simply a carry over?
					// if none of the above, copy data over:
					// { copy }
				} 
				else // this is a new column
				{
					
					// Demand that we require a column size for new columns introduced in the schema.
					if( new String("(no size change)").equals(nextMigrationColumnSize) )
						migiComplainAndExit("Error - Cannot create new column <col> tag without 'size=' attribute.\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + nextMigrationColumnID);

					// Has it been assigned any values or data?
					// if no, the zerofill content.
					// TODO: add filler. ^^^^^^^^^.
					byte [] columnOfBytes = {0,0};
					
					mNewBufferData.put((c+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
					mNewBufferColumnIDs.put((c+DEFAULT_HEADER_SIZE_INDEX), nextMigrationColumnID);
					mNewBufferColumnSizes.put((c+DEFAULT_HEADER_SIZE_INDEX), Integer.parseInt(nextMigrationColumnSize));

					
				}

				// Set these three everytime.
				mNewBufferData.put((i+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
				mNewBufferColumnIDs.put((i+DEFAULT_HEADER_SIZE_INDEX), columnID);
				mNewBufferColumnSizes.put((i+DEFAULT_HEADER_SIZE_INDEX), latestColumnSize);

			}
			
			
			/*
			// for each key do
			for(HashMap.Entry<String, Integer> entry : mXMLCurrentMigrationColumnIDs.entrySet())
			{
				String key = entry.getKey();
				Integer value = entry.getValue();

				if(mListOfHandledColumnIDs.containsKey(key))
					continue;

				handleColumnMigration(key);

				mListOfHandledColumnIDs.put(key, value);
			}

			// for each key do
			for(HashMap.Entry<String, Integer> entry : mXMLNextMigrationColumnIDs.entrySet())
			{
				String key = entry.getKey();
				Integer value = entry.getValue();

				if(mListOfHandledColumnIDs.containsKey(key))
					continue;

				// compare against thaihst.

				mListOfHandledColumnIDs.put(key, value);
			}
*/
			// 2 for loops
		}
		
		

		/*
		{

			migrationForward!!

			compare current migration columns to next migration columns..columns..columns

			[col id="v"]   vs   [col id="v" content="love"]
			[col id="b"]   vs   "null"

			then
			FileVersionHeader = 0x04

			bundle-files
			Save.

			Finish!
		}*/

	}// 1 -> 2
	
	

	private static void handleColumnDeletions( String columnID )
	{
		// Delete column if it does not exist in next migration.
		if( !(mXMLNextMigrationColumnIDs.containsKey(columnID)) )
		{
			Integer indexKey = (Integer) getHashMapKeyFromValue(mCurrentBufferColumnIDs, columnID);
			mNewBufferData.remove(indexKey);
			mNewBufferColumnIDs.remove(indexKey);
			mNewBufferColumnSizes.remove(indexKey);
			
			reIndexNextBuffers();
		}
		else // Column does not exist in future migration, so delete it.
		{
			Integer indexKey = (Integer) getHashMapKeyFromValue(mCurrentBufferColumnIDs, columnID);
			mNewBufferData.remove(indexKey);
			mNewBufferColumnIDs.remove(indexKey);
			mNewBufferColumnSizes.remove(indexKey);
			
			/*
			// slide down remainder keys
			for(int i = indexKey; i<mNewBufferColumnIDs.size()-1; ++i) // TODOssssssssssssssssssssssssss
			{
				mNewBufferData.put(i, mNewBufferData.get(i+1));
				mNewBufferColumnIDs.put(i, mNewBufferColumnIDs.get(i+1));
				mNewBufferColumnSizes.put(i, mNewBufferColumnSizes.get(i+1));
			}
			
			Integer lastKey = mNewBufferColumnIDs.size();
			
			mNewBufferData.remove(lastKey);
			mNewBufferColumnIDs.remove(lastKey);
			mNewBufferColumnSizes.remove(lastKey);
			*/
			
			reIndexNextBuffers();
		}
	}

	private static void handleColumnMigration( String columnID )
	{
		
		/// can we get a delete and swap performed correctly like intended??
		/// what is intended?
		//
		// For example, if we delete one, we should re-index,
		// then swap, then re-index,
		// then reindex.. after everytime, or else you'd get into weird issues..........
		
		
		
		// That being said:: ALL DELETIONS and ADDITIONS should be done first,
		// saving swapping for last.

		// Does next migration have column?
		if(mXMLNextMigrationColumnIDs.containsKey(columnID))
		{
			// has column changed position?
			if(mXMLNextMigrationColumnIndices.containsKey(columnID) != mXMLCurrentMigrationColumnIndices.containsKey(columnID) )
			{
				Integer indexNext = mXMLNextMigrationColumnIndices.get(columnID);
				Integer indexCurrent = mXMLCurrentMigrationColumnIndices.get(columnID);
				
				// backup current
				byte [] backupData = mNewBufferData.get(indexCurrent);
				String backupID    = mNewBufferColumnIDs.get(indexCurrent);
				Integer backupSize = mNewBufferColumnSizes.get(indexCurrent);
				
				// swap //
				// overwrite current //
				mNewBufferData.put(indexCurrent, mCurrentBufferData.get(indexNext));
				mNewBufferColumnIDs.put(indexCurrent, mCurrentBufferColumnIDs.get(indexNext));
				mNewBufferColumnSizes.put(indexCurrent, mCurrentBufferColumnSizes.get(indexNext));
				
				// overwrite next //
				mNewBufferData.put(indexNext, mCurrentBufferData.get(backupData));
				mNewBufferColumnIDs.put(indexNext, mCurrentBufferColumnIDs.get(backupID));
				mNewBufferColumnSizes.put(indexNext, mCurrentBufferColumnSizes.get(backupSize));
			}
			
		}
		else // Column does not exist in future migration, so delete it.
		{
			Integer indexKey = (Integer) getHashMapKeyFromValue(mCurrentBufferColumnIDs, columnID);
			mNewBufferData.remove(indexKey);
			mNewBufferColumnIDs.remove(indexKey);
			mNewBufferColumnSizes.remove(indexKey);
			
			/*
			// slide down remainder keys
			for(int i = indexKey; i<mNewBufferColumnIDs.size()-1; ++i) // TODOssssssssssssssssssssssssss
			{
				mNewBufferData.put(i, mNewBufferData.get(i+1));
				mNewBufferColumnIDs.put(i, mNewBufferColumnIDs.get(i+1));
				mNewBufferColumnSizes.put(i, mNewBufferColumnSizes.get(i+1));
			}
			
			Integer lastKey = mNewBufferColumnIDs.size();
			
			mNewBufferData.remove(lastKey);
			mNewBufferColumnIDs.remove(lastKey);
			mNewBufferColumnSizes.remove(lastKey);
			*/
			
			reIndexNextBuffers();
		}
	}

	private static void reIndexNextBuffers ()
	{
		
		/*
		how to handle swapss..to..to.s
		
		
		how to hanlde:
			Integer indexNext = mXMLNextMigrationColumnIndices.get(columnID);
			Integer indexCurrent = mXMLCurrentMigrationColumnIndices.get(columnID);
		
		*/
		
	}


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

		divideFileByCurrentMigration();
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

		//
		pCurrentMigration += 1; ///// todo debug hereish... but why output
		// initColumnSizes();
		//
		//
		//  mXMLCurrentMigrationColumnIDs
		//

		System.out.println("// initColumnSizes();");

		NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
		int stopMigrationIndex = (pCurrentMigration > nListMigrations.getLength()) ? nListMigrations.getLength() : pCurrentMigration;

		for(int m = 0; m < stopMigrationIndex; m++)
		{
			Node nMigration = nListMigrations.item(m);
			NodeList columnList = ((Element) nMigration).getElementsByTagName("col");
			System.out.println("---");
			for(int c = 0; c < columnList.getLength(); c++)
			{
				String columnSize = calcColumnSizeFromNode(columnList.item(c));
				String columnID   = calcColumnIDFromNode(columnList.item(c), m, c);

				if(mXMLCurrentMigrationColumnIDs.containsKey(columnID))  {
					if(mXMLCurrentMigrationColumnIDs.get(columnID) == m)
						migiComplainAndExit("Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );
				}

				// Log the unique columnIDs and which migration version they support.
				mXMLCurrentMigrationColumnIDs.put(columnID, m);
				mXMLCurrentMigrationColumnIndices.put(columnID, c);

				// Only update sequential sizes if a new size is specified in the migration.
				if( !(new String("(no size change)").equals(columnSize)) )
					mXMLCurrentMigrationColumnSizes.put(c, columnSize);
				else if(m == 0) // First migration requires size attributes for all column tags
					migiComplainAndExit("Error - First migration contains a <col> tag missing a 'size=' attribute.\nProblem column: " + (c+1) );

				System.out.println(mXMLCurrentMigrationColumnSizes.get(c));
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
			String columnSize = calcColumnSizeFromNode(columnList.item(c));
			String columnID   = calcColumnIDFromNode(columnList.item(c), pMigration, c);
			Integer columnIndex = c;
			
			if(mXMLCurrentMigrationColumnIDs.containsKey(columnID))  {
				if(mXMLCurrentMigrationColumnIDs.get(columnID) == pMigration)
					migiComplainAndExit("Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (pMigration+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );
			}

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
			String columnSize = calcColumnSizeFromNode(pCurrentListColumns.item(i));

			// mXMLCurrentMigrationColumnSizes will have already been filled and set at this point,
			// or thrown an error if no sizes were specified in base migration.
			if( !(new String("(no size change)").equals(columnSize) ) )
				columnSize = mXMLCurrentMigrationColumnSizes.get(i);

			Integer latestColumnSize = Integer.parseInt(columnSize);
			byte [] columnOfBytes = new byte[latestColumnSize];
			System.arraycopy(mFileBytes, offset, columnOfBytes, 0, latestColumnSize);

			mCurrentBufferData.put((i+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
			mCurrentBufferColumnIDs.put((i+DEFAULT_HEADER_SIZE_INDEX), columnID);
			mCurrentBufferColumnSizes.put((i+DEFAULT_HEADER_SIZE_INDEX), latestColumnSize);

			/*
			System.out.println("complaint");
			System.out.println(columnOfBytes);
			System.out.println(columnID);
			System.out.println(latestColumnSize);
			*/

			offset += latestColumnSize;
		}
	}

	private static String calcColumnSizeFromNode (Node pColumn)
	{
		NamedNodeMap mapAttrs = pColumn.getAttributes();
		Node nColumnSize = mapAttrs.getNamedItem("size");
		return (nColumnSize != null) ? nColumnSize.getTextContent() : "(no size change)";
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

	private static Node firstMigrationNode () {
		NodeList nList = docXML.getElementsByTagName(mFileID);
		return nList.item(0);
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

			if(new String("-i").equals(mArgs[i])) {
				if(mArgs.length <= i+1) {
					migiComplainAndExit("-i specified with no input path specified.");
				} else {
					mFilename = mArgs[i+1];
				}
			}

			if(new String("-m").equals(mArgs[i])) {
				if(mArgs.length <= i+1) {
					migiComplainAndExit("-m specified with no migration path or file specified.");
				} else {
					mMigrationPath = mArgs[i+1];
				}
			}
		}
		System.out.print("\n");
	}

} // class Migi //


// java Migi -i "file.fun.nes" -m "migrations/"
// java Migi -i "file.fun.nes" -m "C:\Users\Matthew\Desktop\RAD.xml"































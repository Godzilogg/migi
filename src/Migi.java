import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
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

	private static HashMap<Integer, String> mColumnSizes = new HashMap<Integer, String>();
	private static HashMap<Integer, String> mCurrentBuffer = new HashMap<Integer, byte []>();

	public static final Integer DEFAULT_HEADER_SIZE = 8;
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN = "\u001B[32m";

	public static void main ( String [] args )
	{
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

		getFilePaths();

		processBinFile();
		processMigration();

		migrateForward();

		migiComplainAndExit("");
	}

	private static void migrateForward ()
	{
		return;
	}

	private static void processBinFile ()
	{
		try
		{
			Path path = Paths.get(mFilename);
			mFileBytes = Files.readAllBytes(path);

			// Get fileID from bin file and save it for XML processing later.
			byte [] bytesFileID = Arrays.copyOfRange(mFileBytes, 0, 3);
			mFileID = new String(bytesFileID, "ASCII");
			mFileIDVersion = (int)mFileBytes[3];

		}
		catch (FileNotFoundException e) { e.printStackTrace(); }
		catch (IOException e) { e.printStackTrace(); }
	}

	private static void processMigration ()
	{
		try
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

			divideFileBySchema();
		}
		catch (Exception e) { e.printStackTrace(); }
	}

	// divideFileBySchema ()
	//
	// Take file's binary data and sub-divide into columns related to the
	// XML DB schema.
	//
	private static void divideFileBySchema ()
	{
		try
		{
			// Get all migrations for file.
			NodeList nList = docXML.getElementsByTagName(mFileID);

			// Calculate all sizes from first migration
			initSizesColumns();
			

			// Start at current version and migrate forwards
			for(int k = 0; k < nList.getLength(); k++)
			{
				Node nNode = nList.item(k);
				System.out.println("\nRunning Migration: " + nNode.getNodeName() + " v" + k);

				if(nNode.getNodeType() == Node.ELEMENT_NODE)
				{
					NodeList colList = ((Element)nNode).getElementsByTagName("col");

					for(int i = 0; i < colList.getLength(); i++)
					{
						Node column = colList.item(i);
						NamedNodeMap mapAttrs = column.getAttributes();
						Node nColumnSize = mapAttrs.getNamedItem("size");
						String columnSize = "(no size change)";

						if(nColumnSize != null)
							columnSize = nColumnSize.getTextContent();

						System.out.println("  # bytes " + columnSize + " = " + column.getTextContent());


					}

					//System.out.println("Staff id : " + eElement.getAttribute("id"));
					//System.out.println("First Name : " + eElement.getElementsByTagName("firstname").item(0).getTextContent());
					//System.out.println("Last Name : " + eElement.getElementsByTagName("lastname").item(0).getTextContent());
					//System.out.println("Nick Name : " + eElement.getElementsByTagName("nickname").item(0).getTextContent());
					//System.out.println("Salary : " + eElement.getElementsByTagName("salary").item(0).getTextContent());

				}
			}
		}
		catch (Exception e) { e.printStackTrace(); }
	}

	// initSizesColumns ()
	//
	// Runs through first migration to get sizes of all columns
	//
	private static void initSizesColumns ()
	{
		try
		{
			Node nMigration = firstMigrationNode();
			NodeList columnList = ((Element)nMigration).getElementsByTagName("col");

			for(int i = 0; i < columnList.getLength(); i++)
			{
				Node column = columnList.item(i);
				NamedNodeMap mapAttrs = column.getAttributes();
				Node nColumnSize = mapAttrs.getNamedItem("size");
				String columnSize = (nColumnSize != null) ? nColumnSize.getTextContent() : "(no size change)";

				// First migration requires size attributes //
				if( !(new String("(no size change)").equals(columnSize)) )
					mColumnSizes.put(i, columnSize);
				else
					migiComplainAndExit("Error - First migration contains a <col> tag missing a 'size=' attribute.\nProblem column: " + (i+1));
			}
		}
		catch (Exception e) { e.printStackTrace(); }
	}

	private static void divideFileByCurrentMigration ()
	{
		try
		{
			initSizesColumns();
			NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
			Node nCurrentMigration = nList.item(mFileIDVersion);
			NodeList listColumns = ((Element)nCurrentMigration).getElementsByTagName("col");

			Integer offset = initCurrentBufferHeader();


			for(int i = 0; i < listColumns.getLength(); i++)
			{

				// TODO
                // figure size of column here, based on everything -----------------------------------------------------
				Node column = listColumns.item(i);
				NamedNodeMap mapAttrs = column.getAttributes();
				Node nColumnSize = mapAttrs.getNamedItem("size");
				String columnSize = "(no size change)";

				if(nColumnSize != null)
					columnSize = nColumnSize.getTextContent();

				System.out.println("  # bytes " + columnSize + " = " + column.getTextContent());
				// figure size of column here, based on everything------------------------------------------------------


				int headerOffsetIndex = 1;
				int latestColumnSize = mColumnSizes[i] || something_else; // figure size of column here, based on everything

				byte [] columnOfBytes = Arrays.copyOfRange(mFileBytes, offset, latestColumnSize);
				mCurrentBuffer.put((i+headerOffsetIndex), columnOfBytes);

				// mFileID = new String(bytesFileID, "ASCII");
				// mFileIDVersion = (int)mFileBytes[3];
			}

		}
		catch (Exception e) { e.printStackTrace(); }
	}

	private static Integer initCurrentBufferHeader ()
	{
		mCurrentBuffer.clear();

		byte [] bytesFileID = Arrays.copyOfRange(mFileBytes, 0, DEFAULT_HEADER_SIZE);
		mCurrentBuffer.put(i, bytesFileID);

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































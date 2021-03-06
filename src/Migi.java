//vim: ts=2:tw=78: et:
//Then Type into VIM: retab
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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

// TODO: write unit test

// javac -d bin src/*.java
// java -classpath bin Migi -i "test/super_file.bin" -m "test/sample_migration.xml"

// javac -d bin src/*.java && java -classpath bin Migi -i "test/super_file.bin" -m "test/sample_migration.xml"

class Migi
{
  static String mFilename = "";
  static String mMigrationPath = "";
  static String [] mArgs;
  static byte [] mFileBytes;
  static String mFileID = "RAD";
  static Integer mFileIDVersion = 0;
  static Document docXML;
  static boolean mOverwriteFile = false;

  private static HashMap<Integer, Integer> mXMLCurrentMigrationColumnSizes = new HashMap<Integer, Integer>();
  private static HashMap<String, Integer> mXMLCurrentMigrationColumnIDs = new HashMap<String, Integer>();
  private static HashMap<String, Integer> mXMLCurrentMigrationColumnIndices = new HashMap<String, Integer>();

  private static HashMap<Integer, Integer> mXMLNextMigrationColumnSizes = new HashMap<Integer, Integer>();
  private static HashMap<String, Integer> mXMLNextMigrationColumnIDs = new HashMap<String, Integer>();
  private static HashMap<String, Integer> mXMLNextMigrationColumnIndices = new HashMap<String, Integer>();

  private static HashMap<Integer,byte[]> mCurrentBufferData = new HashMap<Integer, byte []>();
  private static HashMap<Integer,String> mCurrentBufferColumnIDs = new HashMap<Integer, String>();
  private static HashMap<Integer,Integer> mCurrentBufferColumnSizes = new HashMap<Integer, Integer>();

  private static HashMap<Integer,byte[]> mNewBufferData = new HashMap<Integer, byte []>();
  private static HashMap<Integer,String> mNewBufferColumnIDs = new HashMap<Integer, String>();
  private static HashMap<Integer,Integer> mNewBufferColumnSizes = new HashMap<Integer, Integer>();

  public static final Integer DEFAULT_HEADER_SIZE = 8;
  public static final Integer DEFAULT_HEADER_SIZE_INDEX = 1;
  public static final Integer BUFFER_HEADER_SIZE = 4;
  public static final Integer BINARY_BLOB_ID = -1;

  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_GREEN = "\u001B[32m";
  public static final String ANSI_RED   = "\u001B[31m";
  public static final String HEADER_ID = "!!!___HEADER___!!!";

  // main ( String [] args )
  //
  // To run open up your terminal and type:
  //      java -classpath bin Migi -i "test/super_file.bin" -m "test/sample_migration.xml"
  // to overwrite file:
  //      java -classpath bin Migi -r -i "test/super_file.bin" -m "test/sample_migration.xml"
  //
  public static void main ( String [] args )
  {
    // TODO: run through an entire migration directory
    //       and migrate every binary file with same name as migration file.

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
      System.out.println("Done");
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

    // Start at current version and migrate forwards
    for(int m = currentFileMigrationVersion; m < (latestXMLMigrationVersion-1); ++m)
    {
      System.out.println("<*>----| Running Migration: #" + (m+1) + " |----<*>\n|");

      Node nNextMigration = nListMigrations.item(m+1);
      NodeList nNextMigrationListColumns = ((Element)nNextMigration).getElementsByTagName("col");

      Node nCurrentMigration = nListMigrations.item(m);
      NodeList nCurrentMigrationListColumns = ((Element)nCurrentMigration).getElementsByTagName("col");

      calcCurrentMigrationColumnIDs(m);
      calcNextMigrationColumnIDs(m+1);

      for(int c = 0; c < nNextMigrationListColumns.getLength(); c++)
      {
        int nextMigrationColumnSize = attributeSizeFromNodeOrPastNode(nNextMigrationListColumns.item(c), (m+1));
        String nextMigrationColumnID = getColumnID(nNextMigrationListColumns.item(c));

        //
        // Skip children columns //
        //
        Node childNode = findNodeByID(nCurrentMigrationListColumns, nextMigrationColumnID);

        // If current migration column is a child
        if(childNode != null && hasColumnParent(childNode))
          continue;

        // If next migration column is a child
        if(hasColumnParent(nNextMigrationListColumns.item(c)))
          continue;

        //
        // TODO: Child columns need their own nested processing.
        //       They need to be able to migrate and move columns inside their own family.
        // Note: A child should not be made able to move to its parent level. It can only change sibling positions.
        //       There should be validations to prevent this.
        //
        // migrateChildColumns();
        //

        System.out.println("| <col id=" + nextMigrationColumnID + " />");

        // if column exists in previous migration
        if( (mXMLCurrentMigrationColumnIDs.containsKey(nextMigrationColumnID)) )
        {
          // get previous migration column and all data associated.
          Integer currentMigrationColumnIndex = mXMLCurrentMigrationColumnIndices.get(nextMigrationColumnID);
          Integer currentMigrationColumnSize  = mCurrentBufferColumnSizes.get(currentMigrationColumnIndex + DEFAULT_HEADER_SIZE_INDEX);
          // String  currentMigrationColumnID    = mCurrentBufferColumnIDs.get(currentMigrationColumnIndex);

          //-----------------------------------------------------------
          // has the column size changed
          //-----------------------------------------------------------
          Integer latestColumnSize;
          Integer copySize;

          if(currentMigrationColumnSize == nextMigrationColumnSize || nextMigrationColumnSize == BINARY_BLOB_ID)
            latestColumnSize = currentMigrationColumnSize;
          else
            latestColumnSize = nextMigrationColumnSize;


          System.out.println("latestColumnSize = " + latestColumnSize + "\n\n");


          // allocate memory with the new size (if anything has changed)
          byte [] nextColumnOfBytes = new byte[latestColumnSize];
          Arrays.fill(nextColumnOfBytes, (byte)0x00);
          byte [] currentColumnOfBytes = mCurrentBufferData.get(currentMigrationColumnIndex+DEFAULT_HEADER_SIZE_INDEX);

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

          // Has the new column been given a default value?
          if(strContent == null || strContent.isEmpty())
          {
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

          //-----------------------------------------------------------
          // has the column been given a value
          //-----------------------------------------------------------

          // TODO:
          Integer nextMigrationColumnSizeInt = nextMigrationColumnSize;
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
        /*
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
         */
      } // for(c)

      System.out.println("|");

      // Pass the buffered data along the migration stack
      copyNextBufferIntoCurrentBuffer();

    } // for(m)

    if(mOverwriteFile)
      saveToFile(mFilename);
    else
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

        /*
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
         */
      }
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

  private static void calcCurrentMigrationColumnIDs (Integer pMigration)
  {
    NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
    Node nMigration = nListMigrations.item(pMigration);
    NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

    for(int c = 0; c < columnList.getLength(); c++)
    {
      String columnID = getColumnID(columnList.item(c));
      int columnSize = attributeSizeFromNodeOrPastNode(columnList.item(c), pMigration);

      mXMLCurrentMigrationColumnSizes.put(c, columnSize);
      mXMLCurrentMigrationColumnIDs.put(columnID, pMigration);
      mXMLCurrentMigrationColumnIndices.put(columnID, c);
    }
  }

  private static void calcNextMigrationColumnIDs (Integer pMigration)
  {
    NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
    Node nMigration = nListMigrations.item(pMigration);
    NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

    for(int c = 0; c < columnList.getLength(); c++)
    {
      String columnID = getColumnID(columnList.item(c));
      int columnSize = attributeSizeFromNodeOrPastNode(columnList.item(c), pMigration);

      mXMLNextMigrationColumnSizes.put(c, columnSize);
      mXMLNextMigrationColumnIDs.put(columnID, pMigration);
      mXMLNextMigrationColumnIndices.put(columnID, c);
    }
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
  // Ensures XML file is valid by Migi standards.
  // Ensures unique column IDs per migration.
  // Ensures HEADER_ID is a reserved word.
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
        Node node = columnList.item(c);

        //
        // Ensure Column ID is present //
        //
        NamedNodeMap attrs = node.getAttributes();
        Node nColumnID = attrs.getNamedItem("id");

        if(nColumnID == null)
          migiComplainAndExit("Validation Error - Column <col> tag missing a 'id=' attribute." + "\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) );

        String columnID = nColumnID.getTextContent();

        //
        // Ensure parent columns do not have a numeric 'size' attribute. (only undefined)
        //
        if (columnHasChildren(node) && attributeSizeFromNodeOrPastNode(node, m) != BINARY_BLOB_ID)
          migiComplainAndExit("Error - Column ID: '" + columnID + "' has a 'size=' numeric value with children!\nParent columns cannot have a numeric 'size' attribute.\nTry 'size=?' or 'size=#'");

        //
        // Ensure no duplicate columns in a single migration
        //
        if(columnIDs.get(columnID) == null)
          columnIDs.put(columnID, c);
        else
          migiComplainAndExit("Validation Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );

        //
        // Ensure no columnID matches HEADER_ID
        //
        if( HEADER_ID.equals(columnID) )
          migiComplainAndExit("Validation Error - Column <col> tag contains an 'id=' with value: " + HEADER_ID + " which is a reserved word." + "\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );
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

    if(nCurrentMigration == null)
      migiComplainAndExit("Error - There is no Migration for file Version: " + mFileIDVersion + "\nHint: " + mFileID + " with version 0x00 would migrate the file from the first migration block to the last migration block.\nStarting the file version at 0x01 would assume the file's binary was already migrated to the first migration, and would proceed onwards from that block.\n(It would skip the first migration block.)");

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
        int columnSize = attributeSizeFromNodeOrPastNode(columnList.item(c), pCurrentMigration);
        String columnID   = getColumnID(columnList.item(c));

        if(mXMLCurrentMigrationColumnIDs.containsKey(columnID))
          if(mXMLCurrentMigrationColumnIDs.get(columnID) == m)
            migiComplainAndExit("Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (m+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );

        // Log the unique columnIDs and which migration version they support.
        mXMLCurrentMigrationColumnIDs.put(columnID, m);
        mXMLCurrentMigrationColumnIndices.put(columnID, c);
        mXMLCurrentMigrationColumnSizes.put(c, columnSize);
      }
    }
  }

  private static void calcNextMigrationColumnAttrs (Integer pMigration)
  {
    NodeList nListMigrations = docXML.getElementsByTagName(mFileID);
    Node nMigration = nListMigrations.item(pMigration);

    if(nMigration == null)
      migiComplainAndExit("Exiting - There is only one <" + mFileID + "> block </" + mFileID + "> migration. Cannot migrate forward.");

    NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

    for(int c = 0; c < columnList.getLength(); c++)
    {
      int columnSize = attributeSizeFromNodeOrPastNode(columnList.item(c), pMigration);
      String columnID   = getColumnID(columnList.item(c));
      Integer columnIndex = c;

      if(mXMLCurrentMigrationColumnIDs.containsKey(columnID))
        if(mXMLCurrentMigrationColumnIDs.get(columnID) == pMigration)
          migiComplainAndExit("Error - More than one <col> tag contains the same 'id=' attribute value inside a single migration." + "\nProblem migration: " + (pMigration+1) + "\nProblem column: " + (c+1) + "\nProblem id: " + columnID );

      mXMLNextMigrationColumnIndices.put(columnID, columnIndex);
      mXMLNextMigrationColumnIDs.put(columnID, pMigration);
      mXMLNextMigrationColumnSizes.put(c, columnSize);
    }
  }

  private static void chunkBinFile (NodeList pCurrentListColumns)
  {
    Integer offset = initCurrentBufferHeader();

    for(int i = 0; i < pCurrentListColumns.getLength(); i++)
    {
      Node currentNode = pCurrentListColumns.item(i);
      String columnID = getColumnID(currentNode);

      // Child columns should eventually be calculated in their own Family[] hash.
      // TODO: method chunkFamilyColumns
      //
      // Skip children columns (making them act like a binary blob)
      if(hasColumnParent(currentNode))
        continue;

      int latestColumnSize = columnFamilySize(currentNode, mFileIDVersion, offset);

      byte [] columnOfBytes = new byte[latestColumnSize];
      System.arraycopy(mFileBytes, offset, columnOfBytes, 0, latestColumnSize);

      // System.out.println("<" + columnID + ">" + "  column index c = " + (i + DEFAULT_HEADER_SIZE_INDEX));
      // System.out.println("latestColumnSize = " + latestColumnSize);

      mCurrentBufferData.put((i+DEFAULT_HEADER_SIZE_INDEX), columnOfBytes);
      mCurrentBufferColumnIDs.put((i+DEFAULT_HEADER_SIZE_INDEX), columnID);
      mCurrentBufferColumnSizes.put((i+DEFAULT_HEADER_SIZE_INDEX), latestColumnSize);

      offset += latestColumnSize;
    }
  }

  private static Integer columnFamilySize (Node pParentNode, Integer mFileIDVersion, Integer pOffset)
  {
    Integer latestColumnSize = 0;

    if (columnHasChildren(pParentNode))
    {
      NodeList children = pParentNode.getChildNodes();

      for (int i = 0; i < children.getLength(); i++)
      {
        Node child = children.item(i);

        if (child.getNodeType() != Node.ELEMENT_NODE)
          continue;

        latestColumnSize += columnFamilySize(child, mFileIDVersion, (pOffset + latestColumnSize));
      }
    }
    else // when no children
    {
      latestColumnSize = attributeSizeFromNodeOrPastNode(pParentNode, mFileIDVersion);

      if(latestColumnSize == BINARY_BLOB_ID)
        latestColumnSize = getBlobSize(pOffset);
    }

    return latestColumnSize;
  }

  private static boolean columnHasChildren (Node pNode)
  {
    NodeList children = pNode.getChildNodes();

    if(children.getLength() == 0)
      return false;

    for(int i = 0; i < children.getLength(); i++)
      if(children.item(i).getNodeType() == Node.ELEMENT_NODE)
        return true;

    return false;
  }

  private static boolean hasColumnParent (Node pNode)
  {
    Node parent = pNode.getParentNode();

    if(parent == null)
      return false;

    return parent.getNodeName().equals("col");
  }

  private static Node findNodeByID (NodeList pNodeList, String pID)
  {
    for(int n = 0; n < pNodeList.getLength(); n++)
    {
      Node node = pNodeList.item(n);

      if(node.getNodeType() != Node.ELEMENT_NODE)
        continue;

      if(getColumnID(node).equals(pID))
        return node;
    }

    return null;
  }

  private static int attributeSizeFromNodeOrPastNode (Node pColumn, int pMigrationNumber)
  {
    String columnID = getColumnID(pColumn);
    NamedNodeMap mapAttrs = pColumn.getAttributes();
    Node nColumnSize = mapAttrs.getNamedItem("size");
    String columnSize = null;

    if(nColumnSize != null)
      return parseSizeString(nColumnSize.getTextContent().trim());

    // search through all the migrations and log the latest size for the specific columnID
    // up to the current migration

    NodeList nListMigrations = docXML.getElementsByTagName(mFileID);

    for(int m = 0; m < pMigrationNumber; m++)
    {
      Node nMigration = nListMigrations.item(m);
      NodeList columnList = ((Element) nMigration).getElementsByTagName("col");

      for(int c = 0; c < columnList.getLength(); c++)
      {
        Node nextColumn = columnList.item(c);
        String nextColumnID = getColumnID(columnList.item(c));
        String nextColumnSize = getSizeString(nextColumn);

        if( (new String(columnID).equals(nextColumnID)) && nextColumnSize != null )
          columnSize = nextColumnSize;
      }
    }

    if(columnSize == null)
      migiComplainAndExit("Error - Column <col> ID: " + columnID + " was never assigned a size= attribute in a migration.");

    return parseSizeString(columnSize);
  }

  private static Integer getBlobSize (Integer pOffset)
  {
    byte [] structBufferHeader = new byte[BUFFER_HEADER_SIZE];
    System.arraycopy(mFileBytes, pOffset, structBufferHeader, 0, BUFFER_HEADER_SIZE);

    Integer sizeBuffer = bytesToInt32(structBufferHeader);
    return (sizeBuffer + BUFFER_HEADER_SIZE);
  }

  private static Integer parseSizeString (String pColumnSize)
  {
    if(isSizeUndefined(pColumnSize))
      return BINARY_BLOB_ID;
    else
      return Integer.parseInt(pColumnSize);
  }

  private static boolean isSizeUndefined (String pColumnSize)
  {
    return (pColumnSize.charAt(0) == '#' || pColumnSize.charAt(0) == '?');
  }

  private static int bytesToInt32(byte [] bytes)
  {
    return (
      ((bytes[0] & 0xff))       |
      ((bytes[1] & 0xff) << 8)  |
      ((bytes[2] & 0xff) << 16) |
      ((bytes[3] & 0xff) << 24)
    );
  }

  private static String getSizeString (Node pColumn) { return getAttribute (pColumn, "size"); }
  private static String getColumnID (Node pColumn) { return getAttribute (pColumn, "id"); }
  private static String getAttribute (Node pColumn, String pAttribute)
  {
    NamedNodeMap ColumnAttrs = pColumn.getAttributes();
    Node ColumnSize = ColumnAttrs.getNamedItem(pAttribute);
    return (ColumnSize == null) ? null : ColumnSize.getTextContent().trim();
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
    System.out.println(ANSI_RED + complaint + ANSI_RESET);
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

      if(new String("-r").equals(mArgs[i]) || new String("-R").equals(mArgs[i]))
        mOverwriteFile = true;

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
    System.out.print("\n\n");
  }

} // class Migi //

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Scanner;

/**
 *  Author: TheGag96
 *
 *  This is a wrapper for HackMew's thumb.bat that adds a few new directives/improvements to the compiler:
 *
 *  - a ".org [address]" directive to allow you to patch your code immediately to a specific place in ROM after compiling.
 *  - a ".rom [filename]" directive that specifies a ROM file to patch every time (overridden if a filename is given in the program arguments).
 *  - an addition to the ".equ" directive that lets you specify at compile-time what the value of a variable by using "<>" as the value.
 *  - ...and possibly more to come at request!
 *
 *  You can run it through a few ways:
 *
 *  - java -jar patchy.jar                          (will prompt for patch and ROM filenames)
 *  - java -jar patchy.jar [.asm file]              (will prompt for ROM file if not specified with .rom in patch)
 *  - java -jar patchy.jar [.asm file] [.gba file]  (will override .rom-specified filename)
 *
 *  Happy hacking!
 */
public class Patchy {
    static final float VERSION = 0.5f;

    ////
    //Important variables
    ////
    private static File ROMFile = null;
    private static String patchText = "";
    private static HashMap<Long, String> patchParts = new HashMap<Long, String>();
    private static HashMap<String, Object> equVars = new HashMap<String, Object>();
    private static Scanner inputReader = new Scanner(System.in);

    public static void main(String[] args) throws FileNotFoundException {

        checkForThumbBat();
        handleFileArguments(args);
        parsePatchForDirectives();
        createSeparatePatchesAndCompile();
        insertCompiledCodeInROM();
        cleanup();

        System.out.println("PATCHY: Done patching! :D");
    }

    private static void checkForThumbBat() {
        File asExe = new File("as.exe");
        File objcopyExe = new File("objcopy.exe");
        File thumbBat = new File("thumb.bat");

        //stop program if the compiling tools aren't found
        if (!asExe.exists() || !objcopyExe.exists() || !thumbBat.exists())
            error(0, "The contents of HackMew's thumb.zip is required for this Patchy to function.");
    }

    private static void handleFileArguments(String[] args) {
        System.out.println("=== Patchy v" + VERSION + " by TheGag96 ===");
        if (args.length == 0) {
            String patchPath = "";

            while (patchPath.equals("") || !Files.exists(Paths.get(patchPath))) {
                System.out.print("Patch file | ");
                patchPath = inputReader.nextLine();
            }

            patchText = readStringFromFile(patchPath);

            //if the .rom directive was never used, ask for the ROM filename now
            if (!patchText.contains(".rom")) {
                while (ROMFile == null || !ROMFile.exists()) {
                    System.out.print("ROM file | ");
                    ROMFile = new File(inputReader.nextLine());
                }
            }

        }
        else if (args.length == 1) {
            if (!Files.exists(Paths.get(args[0]))) {
                error(0, "Given patch files doesn't exist.");
            }
            patchText = readStringFromFile(args[0]);

            //if the .rom directive was never used, ask for the ROM filename now
            if (!patchText.contains(".rom")) {
                while (ROMFile == null || !ROMFile.exists()) {
                    System.out.print("ROM file | ");
                    ROMFile = new File(inputReader.nextLine());
                }
            }
        }
        else if (args.length == 2) {
            ROMFile = new File(args[1]);
            if (!Files.exists(Paths.get(args[0]))) {
                error(0, "Given patch file doesn't exist.");
            }
            patchText = readStringFromFile(args[0]);
            if (!ROMFile.exists()) {
                error(0, "Given ROM file doesn't exist.");
            }
        }
        else {
            error( -1, "Usage: patcher.exe [.asm file] [.gba file]\n" +
                    "Alternatively, just run the program by itself and input the file names manually.");
        }
    }

    private static void parsePatchForDirectives() {
        //strip all comments with regex
        patchText = patchText.replaceAll("(?s)/\\*.*\\*/", "");
        patchText = patchText.replaceAll("@.*\n", "\n");

        Scanner patchReader = new Scanner(patchText);
        Scanner lineScanner;
        int lineNum = 1;
        StringBuilder stringBuilder = new StringBuilder();
        boolean foundOrgOnce = false;
        long lastOrgAddress = -1;

        //use a scanner to read the patch by line
        while (patchReader.hasNextLine()) {

            String line = patchReader.nextLine();

            //we use a second scanner to scan the contents of each line
            lineScanner = new Scanner(line);

            //skip line if it's blank
            if (!lineScanner.hasNext()) {
                lineNum++;
                stringBuilder.append("\n");
                continue;
            }

            String firstWord = lineScanner.next();

            //detect directives and perform their functions
            if (firstWord.equals(".org")) {
                long address = 0;
                if (lineScanner.hasNext()) {
                    String addressString = lineScanner.next();

                    //allow compile-time insertion point choosing
                    if (addressString.equals("<>")) {
                        System.out.print("Where to insert patch part #" + patchParts.size()+1 + "? | ");
                        addressString = inputReader.nextLine();
                    }

                    if (addressString.startsWith("0x")) {   //hex numbers
                        address = Long.parseLong(addressString.substring(2), 16);
                    }
                    else if (equVars.containsKey(addressString)) {  //.equ'd variables
                        Object equValue = equVars.get(addressString);
                        if (equValue instanceof Long) {
                            address = (Long)equValue;
                        }
                        else {
                            error(lineNum, "You can't .org to a string, silly!");
                        }
                    }
                    else {
                        error(lineNum, "\".org\" directive requires a valid hex address or variable.");
                    }

                    //disallow negative numbers
                    if (address < 0) {
                        error(lineNum, "Insertion address can't be negative.");
                    }

                    //wrap up our current patch part and start a new one
                    System.out.println("PATCHY: Found insertion directive to 0x" + Long.toHexString(address));
                    if (foundOrgOnce) {
                        patchParts.put(lastOrgAddress, stringBuilder.append('\n').toString());
                        stringBuilder = new StringBuilder();
                    }

                    //save the address given for when we finish up this next patch part
                    lastOrgAddress = address;
                    foundOrgOnce = true;
                }
                else {
                    error(lineNum, ": You need a valid hex address after \".org\".");
                }
            }
            else if (firstWord.equals(".equ") || firstWord.equals(".set")) {
                String varName = "";
                Object varValue = null;
                lineScanner.useDelimiter(", *");  //comma goes after variable name
                if (lineScanner.hasNext()) {
                    varName = lineScanner.next().trim();
                    lineScanner.reset();
                    lineScanner.next();

                    if (equVars.containsKey(varName)) {
                        error(lineNum, "A variable of this name was already declared.");
                    }

                    if (lineScanner.hasNext()) {
                        //support decimal numbers
                        if (lineScanner.hasNextInt()) {
                            varValue = lineScanner.nextInt();
                        }
                        else {
                            String valueString = lineScanner.next();

                            //support prompt for empty variable
                            if (valueString.equals("<>")) {
                                System.out.print("Value for variable \"" + varName + "\"? | ");
                                valueString = inputReader.nextLine();
                            }

                            //support hex numbers
                            if (valueString.startsWith("0x")) {
                                try {
                                    varValue = Long.parseLong(valueString.substring(2), 16);
                                } catch (NumberFormatException e) {
                                    error(lineNum, "Variable value not a valid hex string.");
                                }
                            }
                            else {
                                try {
                                    varValue = Long.parseLong(valueString);
                                } catch (NumberFormatException e) {
                                    if (valueString.startsWith("\"") && valueString.endsWith("\"")) {
                                        varValue = valueString;
                                    }
                                    else {
                                        System.out.println(valueString);
                                        error(lineNum, "Invalid .equ value.");
                                    }
                                }
                            }
                        }

                        //disallow negative numbers
                        if (varValue instanceof Long && (Long)varValue < 0) {
                            error(lineNum, "Variables cannot be negative.");
                        }

                        equVars.put(varName, varValue);
                    }
                    else {
                        error(lineNum, "Variable after \".org\" must be equal to something.");
                    }
                }
                else {
                    error(lineNum, "\".equ\" must be followed with a variable name (and then a comma).");
                }

                stringBuilder.append(".equ ").append(varName).append(", ").append(varValue).append("\n");
            }
            else if (firstWord.equals(".rom")) {
                if (lineScanner.hasNext()) {
                    if (ROMFile == null) {
                        ROMFile = new File(lineScanner.next());
                        if (!ROMFile.exists()) {
                            error(lineNum, "Specified ROM file does not exist.");
                        }
                    }
                    else {
                        System.out.println("PATCHY: ROM filename specified in patch overridden by one specified by program arguments.");
                    }
                }
                else {
                    error(lineNum, "\".rom\" must be followed with a path to a file.");
                }
            }
            else {
                stringBuilder.append(line).append("\n");
            }


            lineNum++;
        }

        //if .org was never used, ask for where to insert the patch
        if (!foundOrgOnce) {
            System.out.print("Where to insert the patch? | ");
            while (lastOrgAddress == -1) {
                String addressString = inputReader.nextLine();
                try {
                    if (addressString.startsWith("0x")) {
                        lastOrgAddress = Long.parseLong(addressString.substring(2), 16);
                    }
                } catch (NumberFormatException e) {
                    error(0, "Needed a valid hex address as input.");
                }
            }
        }
        patchParts.put(lastOrgAddress, stringBuilder.toString());
        patchReader.close();

        //read .equ variables to each part if they weren't already there just in case
        //i make sure to do this in the order they're declared. hopefully this prevents bugs but i don't think
        //it will catch every edge case.
        for (long address : patchParts.keySet()) {
            String part = patchParts.get(address);
            StringBuilder linesToAdd = new StringBuilder();
            for (String varName : equVars.keySet()) {
                String equLine = ".equ " + varName + ", " + equVars.get(varName) + "\n";
                if (!part.contains(equLine)) {
                    linesToAdd.append(equLine);
                }
            }
            patchParts.put(address, linesToAdd.toString() + part);
        }
    }

    private static void createSeparatePatchesAndCompile() {
        System.out.println("PATCHY: Creating " + patchParts.size() + " file(s) and passing to thumb.bat...");
        for (long address : patchParts.keySet()) {
            File part = new File("0x" + Long.toHexString(address) + ".asm");
            try {
                FileOutputStream ostream = new FileOutputStream(part);
                part.createNewFile();
                ostream.write(patchParts.get(address).getBytes());
                part.deleteOnExit();
                ostream.close();

                //execute compiling tools
                Process process = Runtime.getRuntime().exec("thumb.bat " + part.getName() + " 0x" + Long.toHexString(address) + ".bin");
                process.waitFor();

                //write error messages
                BufferedReader input = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line = input.readLine();
                boolean hadErrors = false;
                if (line == null) {
                    System.out.println("PATCHY: thumb.bat compiled part " + part.getName() + " successfully.");
                }
                else {
                    System.out.println("THUMB.BAT ERROR:");
                    while (line != null) {
                        line = input.readLine();
                        System.out.println("    " + line);
                    }
                    error(0, "Something went wrong during external compilation.");
                }

            } catch (IOException e) {
                e.printStackTrace();
                error(0, "Problem creating/handling patch part " + part.getPath() + " for some reason...");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void insertCompiledCodeInROM() {
        System.out.println("PATCHY: Inserting compiled code into ROM...");
        try {
            RandomAccessFile ROM = new RandomAccessFile(ROMFile, "rws");
            for (long address : patchParts.keySet()) {
                RandomAccessFile patchBin = new RandomAccessFile(new File("0x" + Long.toHexString(address) + ".bin"), "rws");
                byte[] binData = new byte[(int)patchBin.length()];
                ROM.seek(address);
                ROM.write(binData);
                patchBin.close();
            }
            ROM.close();
        } catch (IOException e) {
            e.printStackTrace();
            error(0, "Problem inserting compiled code into ROM.");
        }
    }

    private static void cleanup() {
        System.out.println("PATCHY: Cleaning up...");
        for (long address : patchParts.keySet()) {
            File partBin = new File("0x" + Long.toHexString(address) + ".bin");
            if (!partBin.exists()) continue;
            partBin.deleteOnExit();
        }
        inputReader.close();
    }

    private static void error(int line, String message) {
        if (line == 0)
            System.out.println("PATCHY ERROR: " + message);
        else
            System.out.println("PATCHY ERROR (line " + line + "): " + message);

        System.exit(1);
    }

    private static String readStringFromFile(String path) {
        String result = "";
        try {
            result = new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            e.printStackTrace();
            error(0, "Something went wrong handling the patch file.");
        }

        return result;
    }


}

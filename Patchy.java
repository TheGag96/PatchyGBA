import java.io.*;
import java.util.HashMap;
import java.util.Scanner;

/**
 *  Author: TheGag96
 *
 *  This is a wrapper for Hackmew's thumb.bat that allows you to patch a ROM directly after compilation without having
 *  to manually insert your code. It also allows you to patch code to multiple places with just one file.
 *
 *  It does this using an ".org" directive and it works similarly to the one found in 65c816 assemblers xkas and asar.
 *  The usage is: .org [hex address]
 *  And that's it. it treats each .org'd section of the code as a separate file, compiles them separately, and patches
 *  them to the places in ROM given. This means that you cannot branch in your code to places separated by .orgs.
 *  To accomplish this, I would probably need to simply write my own assembler, and I'm not sure I have the confidence
 *  to do that. :P
 *
 *  Additionally, Patchy allows you write single-line comments with a semicolon (;). I simply hide these comments from
 *  the assembler during patching.
 */
public class Patchy {
    static final float VERSION = 0.1f;
    public static void main(String[] args) throws FileNotFoundException {
        ////
        //Important variables
        ////
        File patchFile = null;
        File ROMFile = null;
        HashMap<Long, String> patchParts = new HashMap<Long, String>();
        //

        ////
        //File input setup and error handling
        ////
        System.out.println("=== Patchy v" + VERSION + " by TheGag96 ===");
        if (args.length == 0) {
            Scanner inputReader = new Scanner(System.in);

            while (patchFile == null || !patchFile.exists()) {
                System.out.print("Patch file | ");
                patchFile = new File(inputReader.nextLine());
            }
            while (ROMFile == null || !ROMFile.exists()) {
                System.out.print("ROM file | ");
                ROMFile = new File(inputReader.nextLine());
            }
            inputReader.close();
        }
        else if (args.length == 2) {
            patchFile = new File(args[0]);
            ROMFile = new File(args[1]);
            if (!patchFile.exists()) {
                error(0, "Given patch file doesn't exist.");
            }
            if (!ROMFile.exists()) {
                error(0, "Given ROM file doesn't exist.");
            }
        }
        else {
            error( -1, "Usage: patcher.exe [.asm file] [.gba file]\n" +
                       "Alternatively, just run the program by itself and input the file names manually.");
        }
        //

        ////
        //Patch parsing
        ////
        Scanner patchReader = new Scanner(patchFile);
        Scanner lineScanner;
        int lineNum = 1;
        StringBuilder stringBuilder = new StringBuilder();
        boolean foundOrgOnce = false;
        long lastOrgAddress = 0;
        while (patchReader.hasNextLine()) {

            String line = patchReader.nextLine();

            //remove ; comments
            if (line.contains(";")) {
                line = line.substring(0, line.indexOf(';'));
            }

            lineScanner = new Scanner(line);
            if (!lineScanner.hasNext()) {
                lineNum++;
                stringBuilder.append("\n");
                continue;
            }

            String firstWord = lineScanner.next();

            //detect .org directive and split up pieces of code in between
            if (firstWord.equals(".org")) {
                long address;
                if (lineScanner.hasNextInt(16)) {
                    address = lineScanner.nextLong(16);
                    System.out.println("PATCHY: Found insertion directive to " + Long.toHexString(address));
                    if (foundOrgOnce) {
                        patchParts.put(lastOrgAddress, stringBuilder.toString());
                        stringBuilder = new StringBuilder();
                    }

                    lastOrgAddress = address;
                    foundOrgOnce = true;
                }
                else {
                    error(lineNum, ": You need a valid hex address after \".org\".");
                }
            }
            else {
                stringBuilder.append(line);
                stringBuilder.append("\n");
            }


            lineNum++;
        }
        if (!foundOrgOnce) {
            error(0, ".org was never used in the patch file. Where do I put the code? :(");
        }
        patchParts.put(lastOrgAddress, stringBuilder.toString());
        patchReader.close();
        //

        ////
        //Build separate patch files and compile
        ////
        System.out.println("PATCHY: Creating " + patchParts.size() + " file(s) and passing to thumb.bat...");
        for (long address : patchParts.keySet()) {
            File part = new File(Long.toHexString(address) + ".asm");
            try {
                FileOutputStream ostream = new FileOutputStream(part);
                part.createNewFile();
                ostream.write(patchParts.get(address).getBytes());
                part.deleteOnExit();
                ostream.close();

                Process process = Runtime.getRuntime().exec("thumb.bat " + part.getName() + " " + Long.toHexString(address) + ".bin");
                process.waitFor();

                BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = "";
                while (true) {
                    line = input.readLine();
                    if (line == null) break;
                    System.out.println("THUMB.BAT: " + line);
                }
                if (process.exitValue() != 0) {
                    error(0, "Something went wrong during external compilation.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                error(0, "Problem creating/handling patch part " + part.getPath() + " for some reason...");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //
        ////
        //Code insertion
        ////
        System.out.println("PATCHY: Inserting compiled code into ROM.");
        try {
            RandomAccessFile ROM = new RandomAccessFile(ROMFile, "rws");
            for (long address : patchParts.keySet()) {
                RandomAccessFile patchBin = new RandomAccessFile(new File(Long.toHexString(address)+".bin"), "rws");
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
        //

        ////
        //Post-run Cleanup
        ////
        for (long address : patchParts.keySet()) {
            File part = new File(Long.toHexString(address) + ".bin");
            part.deleteOnExit();
        }
        //

        System.out.println("PATCHY: Done patching! :D");
        //(new Scanner(System.in)).nextLine();
    }

    private static void error(int line, String message) {
        if (line == 0)
            System.out.println("PATCHY ERROR: " + message);
        else
            System.out.println("PATCHY ERROR (line " + line + "): " + message);

        System.exit(1);
    }


}

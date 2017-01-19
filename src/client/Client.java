package client;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Адреса и порты задаются через командную строку:
 * клиенту --- куда соединяться, серверу --- на каком порту слушать.
 * <p>
 * директория задаётся в командной строке
 */
//java Client port(0) ipAddr(1) dir(2)
public class Client {
    private Socket socket;
    private String name = "client";
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader keyboard;
    private Thread listener;
    private String directory, newDir;
    private ArrayList<String> files = new ArrayList<>();
    private ArrayList<String> myFiles = new ArrayList<>();//список имеющихся файлов, его показываем серверу
    private ArrayList<String> dynamicFolders = new ArrayList<>();//свои папки
    private ArrayList<String> acquiredDynamicFolders = new ArrayList<>();//чужие папки

    public Client(String adr, int port, String dir) throws IOException {
        InetAddress ipAddress = InetAddress.getByName(adr); // создаем объект который отображает вышеописанный IP-адрес
        socket = new Socket(ipAddress, port); // создаем сокет, используя IP-адрес и порт сервера
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        keyboard = new BufferedReader(new InputStreamReader(System.in));
        directory = dir;
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);// порт, к которому привязывается сервер
        //String address = "localhost","127.0.0.1" это IP-адрес сервера
        new Client(args[1], port, args[2]).run();
    }

    private void socketClose() {
        try {
            socket.close();
        } catch (IOException e) {
        }
    }

    public void run() {//отправляет на сервер
        try {
            System.out.println("my directory is " + directory);
            System.out.println("write your name:");
            name = keyboard.readLine();
            out.writeUTF(name);
            System.out.println("Welcome!");
            receiveList(dynamicFolders);
            if (dynamicFolders.size() != 0){
                System.out.println("You have " + dynamicFolders.size() + " dynamic directories.\n" +
                        "Please specify their paths:");
                for (int i = 0; i < dynamicFolders.size(); i++){
                    System.out.println(dynamicFolders.get(i).substring("dynamic_".length()) + ": ");
                    String path = keyboard.readLine();
                    dynamicFolders.set(i, path);
                }
                System.out.println("OK.");
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        try {
            while (true) {
                String line;
                line = keyboard.readLine();
                if (socket.isClosed())
                    break;

               /* if (line.contains("@sendfile")) {
                    String fileName = line.substring("@sendfile".length() + 1);
                    sendFile(fileName);

                } else if (line.contains("@listdirectory")) {//проход по директории и её отправка
                    newDir = directory;
                    File dir = new File(directory);
                    readDirectory(dir);
                    sendAll(files);
                } else*/
                if (line.contains("@addDF")) {
                    String path = line.substring("@addDF".length() + 1);
                    dynamicFolders.add(path);
                } else if (line.contains("@deleteDF")) {
                    String path = line.substring("@deleteDF".length() + 1);
                    dynamicFolders.remove(path);
                } else if (line.contains("@sync")) {
                    out.writeUTF(line);

                    //отправляем серверу свой список, чтобы он удалил того, чего у нас уже нет
                    ArrayList<String> dynListForServer = new ArrayList<>();
                    for (String item : dynamicFolders) {//C:\...\Kurs\adds
                        int slash = item.lastIndexOf("\\");
                        String name = item.substring(slash + 1);
                        dynListForServer.add("dynamic_" + name);
                    }
                    sendList(dynListForServer);
                    //теперь проверка динамич.папок от сервера
                    ArrayList<String> serverDF = new ArrayList<>();
                    receiveList(serverDF);
                    for (String item : acquiredDynamicFolders) {
                        if (!serverDF.contains(item)) {//item - dynamic_name
                            File file = new File(directory + item);
                            deleteDir(file);
                        }
                    }
                    acquiredDynamicFolders.clear();

                    for (String item : serverDF) {
                        if (!dynListForServer.contains(item))
                            acquiredDynamicFolders.add(item);
                    }
                    /////
                    System.out.println(" - ADF : " + acquiredDynamicFolders);
                    System.out.println(" - DF : " + dynamicFolders);
                    /////

                    //отправляем сами файлы
                    for (int i = 0; i < dynamicFolders.size(); i++) {
                        newDir = dynamicFolders.get(i) + "/";
                        System.out.println("Directory: " + newDir);
                        files.clear();
                        File dFile = new File(newDir);
                        readDirectory(dFile);//создаём список того, что находится в папке
                        for (int j = 0; j < files.size(); j++) {
                            files.set(j, files.get(j).substring(newDir.length()));//имя самого файла
                        }
                        ///
                        System.out.println("- in DynDir:");
                        for (String s : files) System.out.println(s);
                        System.out.println("- end DynDir");
                        System.out.println("- in dynListForServer:");
                        for (String s : dynListForServer) System.out.println(s);
                        System.out.println("- end dynListForServer\n");
                        ////отправляем файлы из списка
                        out.writeUTF(dynListForServer.get(i));//имя папки на сервере
                        out.write(files.size());
                        sendAll(files, newDir);
                    }


                    myFiles.clear();
                    files.clear();
                    newDir = directory;
                    File dir = new File(directory);
                    readDirectory(dir);
                    for (String str : files) {
                        String innerStr = str.substring(directory.length());//чтобы убрать "from/"
                        myFiles.add(innerStr);
                        System.out.println(innerStr);///////
                    }//////////////

                    //отправляем список
                    sendList(myFiles);

                    //принимаем список конфликтов
                    ArrayList<String> confl = new ArrayList<>();
                    receiveList(confl);

                    System.out.println("There are " + confl.size() +
                            " already existing files.\nDo you want to update them all?\n(n/y/choose)");
                    String ans = keyboard.readLine();
                    if (ans.equals("n"))
                        confl.clear();
                    else if (ans.equals("choose")) {
                        for (Iterator<String> it = confl.iterator(); it.hasNext(); ) {
                            String item = it.next();
                            System.out.println("!\n" + item + " already exists.\nDo you want to update it? (y/n)");
                            String str = keyboard.readLine();
                            if (str.equals("n"))
                                it.remove();
                        }
                    }
                    for (String str : confl) System.out.println(str);//проверка
                    //отсылаем его обратно
                    sendList(confl);

                    //принимаем файлы от сервера
                    int numberOfFiles = in.read();
                    for (int i = 0; i < numberOfFiles; i++) {
                        String string;
                        string = in.readUTF(); // ждем пока сервер отошлет строку текста
                        if (string.contains("@sendfile")) {//принимаем файл
                            String fileName = string.substring("@sendfile".length() + 1);
                            receiveFile(fileName);
                        } else if (string.contains("@directory")) {
                            String dirName = string.substring("@directory".length() + 1);
                            makeDir(directory + dirName + "/");
                        }
                    }
                    //отсылаем свои файлы на сервер
                    out.write(myFiles.size());
                    sendAll(myFiles, directory);

                    //обновляем список
                    newDir = directory;
                    dir = new File(directory);
                    readDirectory(dir);
                } else if (line.contains("@directory")) {
                    out.writeUTF(line);
                    out.flush();

                } else if (line.contains("@read")) {//просто составляем список директории
                    files.clear();
                    myFiles.clear();
                    newDir = directory;
                    File dir = new File(directory);
                    readDirectory(dir);
                    for (String str : files) {
                        String innerStr = str.substring(directory.length());//чтобы убрать "from/"
                        myFiles.add(innerStr);
                        System.out.println(innerStr);
                    }
                } else if (line.contains("@delete")) {
                    String fileName = line.substring("@delete".length() + 1);
                    File test = new File(directory + fileName);
                    deleteDir(test);
                }

                if (line.equals("@quit")) {
                    out.writeUTF(line);
                    socketClose();
                    break;
                }
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    private void sendAll(ArrayList<String> list, String directory) throws IOException {
        for (String fileName : list) {
            File envelope = new File(directory + fileName);
            if (envelope.isFile())
                sendFile(fileName, directory);
            else if (envelope.isDirectory()) {
                out.writeUTF("@directory " + fileName);
                out.flush();
            }
        }
    }

    private void receiveList(ArrayList<String> list) throws IOException {
        int listSize = in.read();
        for (int i = 0; i < listSize; i++)
            list.add(in.readUTF());
    }

    private void sendList(ArrayList<String> list) throws IOException {
        out.write(list.size());
        for (String str : list) {
            out.writeUTF(str);
        }
    }

    private void sendFile(String fileName, String directory) throws IOException {
        File file = new File(directory + fileName);

        try {
            FileInputStream inputFile = new FileInputStream(file);
            out.writeUTF("@sendfile " + fileName); // отсылаем серверу, если такой файл есть
            out.flush();
            out.writeLong(file.length());//отправляем размер
            byte[] buf = new byte[65536];
            int count;
            while ((count = inputFile.read(buf)) != -1) {
                out.write(buf, 0, count);//отсылаем файл
                out.flush();
            }
            System.out.println("The file was sent");
            inputFile.close();
        } catch (FileNotFoundException n) {
            System.out.println("There is no such file");
        }
    }

    private void readDirectory(File folder) {
        File[] list = folder.listFiles();//список того, что в папке folder
        for (File file : list) {
            if (file.isDirectory()) {
                int end = newDir.length();
                newDir = newDir + file.getName() + "/";
                readDirectory(file);//рекурсия
                newDir = newDir.substring(0, end);
            }
            //System.out.println(newDir + file.getName());
            files.add(newDir + file.getName());
        }
    }

    private void makeDir(String path) {
        int end = 0;
        while (true) {
            end = path.indexOf("/", end);
            if (end == -1)
                break;
            String nameDir = path.substring(0, end);
            File folder = new File(nameDir);
            if (!folder.isDirectory()) {//если такой папки нет
                folder.mkdir();
            }
            end++;
        }
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] list = dir.listFiles();
            if (list != null) {
                for (File file : list) {
                    deleteDir(file);
                }
            }
            dir.delete();
        } else {
            dir.delete();
        }
    }

    private void receiveFile(String fileName) throws IOException {

        long size = in.readLong();
        long testSize = size;//для получения точного размера из потока
        System.out.println("\nreceiving file " + fileName + " size = " + size + " bytes");

        int end = fileName.lastIndexOf("/");
        if (end != -1) { //если файл не в корневой папке, а в подпапке
            String nameDir = fileName.substring(0, end);
            File sample = new File(directory + nameDir);
            if (!sample.isDirectory())//такой директории нет
                makeDir(directory + nameDir + "/");
        }

        byte[] buf = new byte[65536];
        FileOutputStream outputFile = new FileOutputStream(directory + fileName);
        int count;
        long all = 0;
        double limit = Math.ceil((double) size / 65536);
        System.out.print("limit = " + (int) limit + "; ");
        while (all < size) {
            int readSize = (int) Math.min(testSize, buf.length);//чтобы не считать боьше, чем нужно
            count = in.read(buf, 0, readSize);
            all += count;
            testSize -= count;
            outputFile.write(buf, 0, count);//записываем файл
            outputFile.flush();
            if (all == size) {
                System.out.println("received FULL size");
                break;
            }
        }
        System.out.println("received \"" + fileName + "\" (" + all + " bytes) from server");
        outputFile.close();
    }

}

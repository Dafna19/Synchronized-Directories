package server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * здесь происходит взаимодействие с клиентом
 */
class ServerDispatcher extends Thread {
    private ConcurrentHashMap<String, ArrayList<String>> allClients;
    private Socket socket;
    public DataInputStream in;
    public DataOutputStream out;
    private String myName = "";
    private SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss z dd.MM.yyyy");
    private FileWriter logFile;
    private ArrayList<String> myFiles;//список имеющихся у сервера файлов
    private ArrayList<String> filesToClient = new ArrayList<>();//список файлов, которые надо отправить
    private ArrayList<String> conflictedFiles = new ArrayList<>();//список файлов, о которых надо спросить
    private ArrayList<String> dynamicFolders;//все динам.папки
    private ArrayList<String> myDynamicFolders = new ArrayList<>();//папки этого клиента
    private String directory, newDir;

    public ServerDispatcher(Socket s, ArrayList<String> listDF,
                            ConcurrentHashMap<String, ArrayList<String>> clients,
                            FileWriter log, ArrayList<String> serverFiles) {
        logFile = log;
        socket = s;
        myFiles = serverFiles;
        dynamicFolders = listDF;
        allClients = clients;
        directory = "server/";
        newDir = directory;
        File dir = new File(directory);
        try {
            readDirectory(dir);//заполнили свой список

            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (NullPointerException n) {
        }
    }

    public void run() {
        try {
            myName = in.readUTF();
            if (allClients.containsKey(myName))//если мы уже заходили
                myDynamicFolders = allClients.get(myName);
            else//если первый раз
                allClients.put(myName, myDynamicFolders);
            sendList(myDynamicFolders);
            logFile.write("\nNew client \"" + myName + "\" ip: " + socket.getLocalAddress() +
                    " port: " + socket.getPort() + " at " + date.format(new Date()));
            logFile.flush();

            while (true) {
                try {
                    String line;
                    line = in.readUTF();
                    if (line.contains("@quit")) {
                        logFile.write("\nClient \"" + myName + "\" ip:" + socket.getLocalAddress() +
                                " port: " + socket.getPort() + " came out at " + date.format(new Date()));
                        logFile.flush();
                        break;

                    } else if (line.contains("@sendfile")) {//принимаем файл
                        String fileName = line.substring("@sendfile".length() + 1);
                        receiveFile(fileName);
                    } else if (line.contains("@directory")) {
                        String dirName = line.substring("@directory".length() + 1);
                        makeDir(directory + dirName + "/");
                    } else if (line.contains("@sync")) {
                        ArrayList<String> tmpClient = new ArrayList<>();
                        receiveList(tmpClient);//список папок клиента

                        for (String name : myDynamicFolders) {//удаляем уже отключенные
                            if (!tmpClient.contains(name)) {
                                File file = new File(directory + name);
                                deleteDir(file);
                                dynamicFolders.remove(name);
                            }
                        }
                        myDynamicFolders.clear();
                        for (String item : tmpClient) {
                            if (!dynamicFolders.contains(item))
                                dynamicFolders.add(item);
                            myDynamicFolders.add(item);//чтобы себе не отправлять
                        }
                        sendList(dynamicFolders);//отправка своего общего списка

                        for (int i = 0; i < myDynamicFolders.size(); i++) {//принимаем файлы
                            String folder = in.readUTF();//имя папки
                            int numberOfFiles = in.read();
                            for (int j = 0; j < numberOfFiles; j++) {
                                String string;
                                string = in.readUTF();
                                if (string.contains("@sendfile")) {//принимаем файл
                                    String fileName = folder + "/" + string.substring("@sendfile".length() + 1);
                                    receiveFile(fileName);
                                } else if (string.contains("@directory")) {
                                    String dirName = folder + "/" + string.substring("@directory".length() + 1);
                                    makeDir(directory + dirName + "/");
                                }
                            }
                        }


                        myFiles.clear();
                        newDir = directory;
                        File dir = new File(directory);
                        readDirectory(dir);//заполнили свой список

                        //удаляем динам. папки этого клиента
                        for (int i = 0; i < myFiles.size(); i++) {
                            int begin = directory.length();
                            int slash = myFiles.get(i).indexOf("/", begin + 1);
                            if (slash == -1)
                                slash = myFiles.get(i).length();
                            String name = myFiles.get(i).substring(begin, slash);
                            if (myDynamicFolders.contains(name)) {
                                myFiles.remove(i);
                                i--;
                            }
                        }

                        //принимаем список клиента
                        ArrayList<String> clientList = new ArrayList<>();
                        receiveList(clientList);

                        /*System.out.println("\n\tclientList");
                        for (String str : clientList) {
                            if (myFiles.contains(directory + str))
                                System.out.print(" + ");
                            System.out.println(str);//проверка
                        }*/

                        for (String servFile : myFiles) {
                            String file = servFile.substring(directory.length());
                            if (clientList.contains(file))
                                conflictedFiles.add(file);
                            else//у клиента такого файла нет
                                filesToClient.add(servFile);
                        }


                        //отправляем список конфликтных
                        sendList(conflictedFiles);
                        //принимаем ответ
                        conflictedFiles.clear();
                        receiveList(conflictedFiles);
                        for (String str : conflictedFiles) {
                            filesToClient.add(directory + str);
                        }
                        //отправляем файлы из filesToClient
                        out.write(filesToClient.size());//сколько файлов
                        sendAll(filesToClient);

                        filesToClient.clear();
                        conflictedFiles.clear();
                        //принимаем файлы от клиента
                        int numberOfFiles = in.read();
                        for (int i = 0; i < numberOfFiles; i++) {
                            String string;
                            string = in.readUTF();
                            if (string.contains("@sendfile")) {//принимаем файл
                                String fileName = string.substring("@sendfile".length() + 1);
                                receiveFile(fileName);
                            } else if (string.contains("@directory")) {
                                String dirName = string.substring("@directory".length() + 1);
                                makeDir(directory + dirName + "/");
                            }
                        }
                        //обновляем свой список
                        myFiles.clear();
                        newDir = directory;
                        dir = new File(directory);
                        readDirectory(dir);

                    }


                } catch (EOFException e) {
                    System.out.println("Something went wrong");
                    e.printStackTrace();
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
            System.out.println("Fatal error");
            e.printStackTrace();
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

    private void receiveFile(String fileName) throws IOException {
        long size = in.readLong();
        long testSize = size;//для получения точного размера из потока
        //System.out.println("\nreceiving file " + fileName + " size = " + size + " bytes");

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
        while (all < size) {
            int readSize = (int) Math.min(testSize, buf.length);//чтобы не считать боьше, чем нужно
            count = in.read(buf, 0, readSize);
            all += count;
            testSize -= count;
            outputFile.write(buf, 0, count);//записываем файл
            outputFile.flush();
            if (all == size) {
                // System.out.println("received FULL size");
                break;
            }
        }
        // System.out.println("received \"" + fileName + "\" (" + all + " bytes)");
        outputFile.close();
        logFile.write("\nReceived file \"" + fileName + "\" (" + all + " bytes) from ip: " +
                socket.getLocalAddress() + " port: " + socket.getPort() +
                " at " + date.format(new Date()));
        logFile.flush();
    }

    protected void makeDir(String path) {
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

    private void sendAll(ArrayList<String> list) throws IOException {//отправляет все файлы из списка
        for (String letter : list) {
            File envelope = new File(letter);
            String innerLetter = letter.substring(directory.length());//чтобы убрать "server/"
            if (envelope.isFile())
                sendFile(innerLetter);
            else if (envelope.isDirectory()) {
                out.writeUTF("@directory " + innerLetter);
                out.flush();
            }
        }
    }

    private void sendFile(String fileName) throws IOException {
        File file = new File(directory + fileName);

        try {
            FileInputStream inputFile = new FileInputStream(file);
            out.writeUTF("@sendfile " + fileName); // отсылаем, если такой файл есть
            out.flush();
            out.writeLong(file.length());//отправляем размер
            byte[] buf = new byte[65536];
            int count;
            while ((count = inputFile.read(buf)) != -1) {
                out.write(buf, 0, count);//отсылаем файл
                out.flush();
            }
            //System.out.println("The file was sent");
            inputFile.close();
            logFile.write("\nSend file \"" + fileName + "\" (" + file.length() + " bytes) to ip: " +
                    socket.getLocalAddress() + " port: " + socket.getPort() +
                    " at " + date.format(new Date()));
            logFile.flush();
        } catch (FileNotFoundException n) {
            System.out.println("There is no such file");
        }
    }

    private void readDirectory(File folder) {
        File[] list = folder.listFiles();//список того, что в папке folder
        if (list != null) {
            for (File file : list) {
                if (file.isDirectory()) {
                    int end = newDir.length();
                    newDir = newDir + file.getName() + "/";
                    readDirectory(file);//рекурсия
                    newDir = newDir.substring(0, end);
                }
                myFiles.add(newDir + file.getName());
            }
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
        } else dir.delete();
    }
}

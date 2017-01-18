package server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * здесь происходит пересылка другим клиентам
 */
public class ServerDispatcher extends Thread {
    private ConcurrentHashMap<String, Socket> allClients;
    private Socket socket;
    private String myName = "";
    public DataInputStream in;
    public DataOutputStream out;
    private SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss z dd.MM.yyyy");
    private FileWriter logFile;
    private ArrayList<String> myFiles;//список имеющихся у сервера файлов
    private ArrayList<String> filesToClient = new ArrayList<>();//список файлов, которые надо отправить
    private ArrayList<String> conflictedFiles = new ArrayList<>();//список файлов, о которых надо спросить
    private String directory, newDir;

    public ServerDispatcher(Socket s, ConcurrentHashMap<String, Socket> list, FileWriter log, ArrayList<String> serverFiles) {
        logFile = log;
        socket = s;
        allClients = list;
        myFiles = serverFiles;
        directory = "server/";
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            myName = in.readUTF();
            allClients.put(myName, socket);//добавили себя в список
            logFile.write("\nNew client \"" + myName + "\" ip: " + socket.getLocalAddress() + " port: " + socket.getPort() + " at " + date.format(new Date()));
            logFile.flush();

            while (true) {
                try {
                    String line;
                    line = in.readUTF();
                    if (line.contains("@quit")) {
                        sendAll(myName + " came out");
                        allClients.remove(myName);
                        logFile.write("\nClient \"" + myName + "\" came out at " + date.format(new Date()));
                        logFile.flush();
                        break;

                    } else if (line.contains("@sendfile")) {//принимаем файл
                        String fileName = line.substring("@sendfile".length() + 1);
                        receiveFile(fileName);
                    }

                    /* else if (line.contains("@sendfile")) {//отправляем файл
                        sendAll(line);//переправляем всем
                        //хотя в принципе, отвалившийся должен удалиться уже здесь
                        sendAll(myName);
                        long size = in.readLong();
                        long testSize = size;//для получения точного размера из потока
                        System.out.println(" size = " + size);
                        //рассылаем всем размер
                        for (Socket s : allClients.values())
                            if (!s.equals(socket)) {//если кто-то плохо вышел, здесь упадёт
                                try {
                                    new DataOutputStream(s.getOutputStream()).writeLong(size);
                                } catch (IOException e) {
                                    for (String candidate : allClients.keySet()) {
                                        if (allClients.get(candidate).equals(s)) {
                                            allClients.remove(candidate);
                                            System.out.println(candidate + " is removed");
                                        }//теперь не упадёт
                                    }
                                }
                            }

                        byte[] buf = new byte[65536];
                        int count;
                        long all = 0;
                        double limit = Math.ceil((double) size / 65536);//количество необходимых пакетов
                        System.out.println("limit = " + (int) limit);
                        int i = 0;
                        while (true) {
                            int readSize = (int) Math.min(testSize, buf.length);//чтобы не считать боьше, чем нужно
                            count = in.read(buf, 0, readSize);//сколько прочитали в пакете
                            all += count;
                            testSize -= count;
                            System.out.println(" count = " + count + "  for read = " + readSize + "  all = " + all + "  i = " + i + "  left = " + testSize);
                            for (Socket s : allClients.values())
                                if (!s.equals(socket))
                                    try {
                                        //посылаем всем файл по частям
                                        new DataOutputStream(s.getOutputStream()).write(buf, 0, count);
                                    } catch (SocketException z) {
                                        System.out.println(" can't send file ");
                                        z.printStackTrace();
                                        break;
                                    }
                            if (all == size)
                                break;
                            i++;
                        }
                        System.out.println(" sent " + all + " bytes");
                        logFile.write("\nClient \"" + myName + "\" sent a file (" + all + " bytes) at " + date.format(new Date()));

                    } */
                    else if (line.contains("@directory")) {
                        String dirName = line.substring("@directory".length() + 1);
                        makeDir(directory + dirName + "/");
                    } else if (line.contains("@sync")) {
                        newDir = directory;
                        File dir = new File(directory);
                        readDirectory(dir);//заполнили свой список

                        //принимаем список клиента
                        ArrayList<String> clientList = new ArrayList<>();;
                        receiveList(clientList);

                        for (String str : clientList) {
                            if (myFiles.contains(directory + str))
                                System.out.print(" + ");
                            System.out.println(str);//проверка
                        }

                        for (String servFile : myFiles) {
                            String file = servFile.substring(directory.length());
                            if (clientList.contains(file))
                                conflictedFiles.add(file);
                            else//у клиента такого файла нет
                                filesToClient.add(servFile);
                        }

                        ////
                        System.out.println("\nconflictedFiles");
                        for (String str : conflictedFiles)
                            System.out.println(str);
                        System.out.println("\nfilesToClient");
                        for (String str : filesToClient)
                            System.out.println(str);
                        ////

                        //отправляем список конфликтных
                        sendList(conflictedFiles);
                        //принимаем ответ
                        conflictedFiles.clear();
                        receiveList(conflictedFiles);
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
        for (String str : list){
            out.writeUTF(str);
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
        System.out.println("received \"" + fileName + "\" (" + all + " bytes) from " + myName);
        outputFile.close();
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

    private void sendAll(String line) {//отправляет всем

        for (Socket s : allClients.values())
            if (!s.equals(socket))
                try {
                    new DataOutputStream(s.getOutputStream()).writeUTF(line);
                } catch (IOException e) {
                    System.out.println("Error while sending \"" + line + "\" to " + s);
                    System.out.println("Connection with client is broken");
                    //если кто-то плохо вышел - удалим его
                    for (String candidate : allClients.keySet()) {
                        if (allClients.get(candidate).equals(s)) {
                            allClients.remove(candidate);
                            System.out.println(candidate + " is removed");
                        }
                    }
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
            myFiles.add(newDir + file.getName());
        }
    }
}

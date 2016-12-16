import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

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
    ArrayList<String> files = new ArrayList<>();

    public Client(String adr, int port, String dir) throws IOException {
        InetAddress ipAddress = InetAddress.getByName(adr); // создаем объект который отображает вышеописанный IP-адрес
        socket = new Socket(ipAddress, port); // создаем сокет, используя IP-адрес и порт сервера
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        keyboard = new BufferedReader(new InputStreamReader(System.in));
        listener = new Thread(new FromServer());
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
            ///временно
            System.out.println("write your directory:");
            directory = keyboard.readLine();
            System.out.println("my directory is " + directory);
            ///

            System.out.println("write your name:");
            name = keyboard.readLine();
            out.writeUTF(name);
            listener.start();
            System.out.println("Welcome!");
        } catch (Exception x) {
            x.printStackTrace();
        }
        try {
            while (true) {
                String line;
                line = keyboard.readLine();
                if (socket.isClosed())
                    break;

                if (line.contains("@sendfile")) {
                    String fileName = line.substring("@sendfile".length() + 1);
                    sendFile(fileName);

                } else if (line.contains("@listdirectory")) {//проход по директории и её отправка
                    newDir = directory;
                    File dir = new File(directory);
                    readDirectory(dir);
                    for (String letter : files) {
                        File envelope = new File(letter);
                        String innerLetter = letter.substring(directory.length());//чтобы убрать "from/"
                        if (envelope.isFile())
                            sendFile(innerLetter);
                        else if (envelope.isDirectory()) {
                            out.writeUTF("@directory " + innerLetter);
                            out.flush();
                        }
                    }
                } else if (line.contains("@directory")) {
                    out.writeUTF(line);
                    out.flush();
                }
                if (line.equals("@quit")) {
                    socketClose();
                    break;
                }
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    private void sendFile(String fileName) throws IOException {
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

    void readDirectory(File folder) {
        File[] list = folder.listFiles();//список того, что в папке folder
        for (File file : list) {
            if (file.isDirectory()) {
                int end = newDir.length();
                newDir = newDir + file.getName() + "/";
                readDirectory(file);//рекурсия
                newDir = newDir.substring(0, end);
            }
            System.out.println(newDir + file.getName());
            files.add(newDir + file.getName());
        }
    }

    private class FromServer implements Runnable {//принимает сообщения

        public void run() {
            try {
                while (true) {
                    String line;
                    line = in.readUTF(); // ждем пока сервер отошлет строку текста


                    // при приёме файла:
                    // @sendfile имя
                    // имя отправителя
                    // размер
                    // файл
                    if (line.contains("@sendfile")) {//принимаем файл
                        String fileName = line.substring("@sendfile".length() + 1);
                        String name = in.readUTF();
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
                        System.out.println("received \"" + fileName + "\" (" + all + " bytes) from " + name);
                        outputFile.close();

                    } else if (line.contains("@directory")) {
                        String dirName = line.substring("@directory".length() + 1);
                        makeDir(directory + dirName + "/");
                    } else
                        System.out.println(line);
                }
            } catch (Exception e) {
                socketClose();
            } finally {
                socketClose();
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

    }

}

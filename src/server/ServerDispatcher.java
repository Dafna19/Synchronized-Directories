package server;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
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

    public ServerDispatcher(Socket s, ConcurrentHashMap<String, Socket> list, FileWriter log) {
        logFile = log;
        socket = s;
        allClients = list;
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

                    } else if (line.contains("@sendfile")) {//отправляем файл
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

                    } else if (line.contains("@directory")) {
                        sendAll(line);
                    }
                    //else sendAll(myName + ": " + line);

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
}

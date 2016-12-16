import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * сервер должен писать в лог (текстовый файл) о освоих действиях
 */
//java Server port(0)
public class Server {
    private ConcurrentHashMap<String, Socket> clients = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private FileWriter logFile;
    private SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss z dd.MM.yyyy");


    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        logFile = new FileWriter("serverLog.txt", true);//дозаписывает в конец
        logFile.write("\n\nServer started at ip: " + serverSocket.getInetAddress() + " port: " + port + " at " + date.format(new Date()));
        logFile.flush();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("I'm working...");
        int port = Integer.parseInt(args[0]);
        new Server(port).run();
    }

    public void run() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();    //подсоединение
                System.out.println("New client");
                ServerDispatcher client = new ServerDispatcher(socket, clients, logFile);
                client.start(); //запускаем
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

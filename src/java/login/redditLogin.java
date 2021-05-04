package login;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = "redditLogin", urlPatterns = {"/redditLogin"})
public class redditLogin extends HttpServlet {

    private PreparedStatement ps = null;
    private PreparedStatement addCodeSt = null;
    private static final String dbURL = "jdbc:derby://localhost:1527/web-agg";
    private static final String dbUser = "root";
    private static final String dbPass = "password";
    private static Connection conn = null;

    @Override
    public void init() throws ServletException {
        initJDBC();
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        //Authenticate User
        String user = request.getParameter("email");
        String pass = request.getParameter("pass");
        boolean auth = checkUser(user, pass);

        //Prepare redirect URL for authorization
        String loginURL = null;
        if (auth) {
            String RANDOM_STRING = generateRandomString();

            if (!addUserStateCode(user, RANDOM_STRING)) {
                auth = false;
            }

            String CLIENT_ID = "KLQrwUIQyPgHlg";
            String TYPE = "code";
            String URI = "http://localhost:25867/CST3511-CW1/redditLoginRedirect";
            String DURATION = "temporary";
            String SCOPE_STRING = "read";

            loginURL = "https://www.reddit.com/api/v1/authorize?client_id=" + CLIENT_ID
                    + "&response_type=" + TYPE
                    + "&state=" + RANDOM_STRING
                    + "&redirect_uri=" + URI
                    + "&duration=" + DURATION
                    + "&scope=" + SCOPE_STRING;
        }

        //Print out URL for redirect
        try (PrintWriter out = response.getWriter()) {
            /* TODO output your page here. You may use following sample code. */
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Redirecting to Reddit Login</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>Reddit Login</h1>");
            if (auth) {
                out.println("<meta http-equiv=\"Refresh\" content=\"0; url='" + loginURL + "'\" />");
            } else {
                out.println("<h2>Authentication Failed</h2>");
            }
            out.println("</body>");
            out.println("</html>");
        }
    }

    //Intializes PreparedStatements to be used by checkUser() and addUserStateCode()
    public void initJDBC() {
        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver").newInstance();
            conn = DriverManager.getConnection(dbURL, dbUser, dbPass);
            conn.setAutoCommit(false);

            ps = conn.prepareStatement("SELECT * FROM users WHERE email = ? AND password = ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            addCodeSt = conn.prepareStatement("UPDATE users SET statecode = ? WHERE email = ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | SQLException ex) {
            ex.printStackTrace();
        }
    }

    //returns true if user and password is in the database, else false
    public boolean checkUser(String user, String pass) {
        try {
            ps.setString(1, user);
            ps.setString(2, pass);
            ResultSet query = ps.executeQuery();

            int size = 0;
            if (query != null) {
                query.last();          // moves cursor to the last row
                size = query.getRow(); // get row id 
            }

            ps.close();
            query.close();
            if (size == 1) {
                return true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        return false;
    }

    //returns true if user statecode was added to database, else false
    public boolean addUserStateCode(String user, String code) {
        try {
            addCodeSt.setString(1, code);
            addCodeSt.setString(2, user);
            if (addCodeSt.executeUpdate() == 0) {
                System.out.println("Error with addUserStateCode");
                return false;
            }

            conn.commit();
            addCodeSt.close();

            return true;
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    //generates a random 32 character alphabet string 
    public String generateRandomString() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 32;
        Random random = new Random();

        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        return generatedString;
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}

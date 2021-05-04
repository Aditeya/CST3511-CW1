package login;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@WebServlet(name = "redditLoginRedirect", urlPatterns = {"/redditLoginRedirect"})
public class redditLoginRedirect extends HttpServlet {

    private PreparedStatement ps = null;
    private PreparedStatement addTokenSt = null;
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

        //Get response parameters
        String error = request.getParameter("error");
        String code = request.getParameter("code");
        String state = request.getParameter("state");

        //print out errors
        if (error != null) {
            System.out.println("-----ERROR-----: " + error);
        }
        
        //Authenticate user
        String user = checkUser(state);
        boolean auth = !user.equals("not_found");
        
        //Token retreival
        boolean tokenStored = false;
        if (auth) {
            //Prepare token retreival URL
            String url = "https://www.reddit.com/api/v1/access_token";
            String rURI = "http://localhost:25867/CST3511-CW1/redditLoginRedirect";
            String urlPostParameters = "grant_type=authorization_code&code=" + code + "&redirect_uri=" + rURI;

            //Encode app ID and secret
            String encoding = Base64.getEncoder().encodeToString(("KLQrwUIQyPgHlg:R3JLZ098SMS25cw6MAHeZ4l-Ss9QNA").getBytes("UTF-8"));

            //Create URL object
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

            connection.setRequestMethod("POST");
            connection.addRequestProperty("User-Agent", "web-agg by /u/Mr_Patcher");    //Custom User Agent
            connection.addRequestProperty("Authorization", "Basic " + encoding);        //Authorization Header
            
            //Adding urlPostParameters for token request
            connection.setDoOutput(true);
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                outputStream.writeBytes(urlPostParameters);

                outputStream.flush();
            }

            //Running Connection
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                //Building response json
                StringBuilder tokenResponse = new StringBuilder();
                try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;

                    while ((inputLine = inputReader.readLine()) != null) {
                        tokenResponse.append(inputLine);
                    }
                }

                try {
                    //Reading Token and saving it to database
                    JSONParser jsonParser = new JSONParser();
                    JSONObject json = (JSONObject) jsonParser.parse(tokenResponse.toString());
                    String token = (String) json.get("access_token");
                    tokenStored = addToken(user, token);

                    //Saving statecode as a cookie with name "web-agg" which expires in an hour
                    Cookie cstate = new Cookie("web-agg", state);
                    cstate.setMaxAge(3600);
                    response.addCookie(cstate);
                } catch (ParseException ex) {
                    ex.printStackTrace();
                }
            } else {
                System.out.println("-----Response Code : " + responseCode);
            }
        }

        //Printing out response to redirect to feed
        try (PrintWriter out = response.getWriter()) {
            /* TODO output your page here. You may use following sample code. */
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Logging In to Reddit</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>Reddit Login Redirect at</h1>");

            if (error != null) {
                switch (error) {
                    case "access_denied":
                        out.println("<h2>Access Denied</h2>");
                        break;
                    case "unsupported_response_type":
                        out.println("<h2>Unsupported Response Type</h2>");
                        break;
                    case "invalid_scope":
                        out.println("<h2>Invalid Scope</h2>");
                        break;
                    case "invalid_request":
                        out.println("<h2>Invalid Request</h2>");
                        break;
                    default:
                        break;
                }
            }

            if (!auth) {
                out.println("<h2>User not found</h2>");
            } else if (!tokenStored) {
                out.println("<h2>token not stored</h2>");
            } else {
                //token stored
                out.println("<meta http-equiv=\"Refresh\" content=\"0; url='faces/feed.xhtml'\" />");
            }

            out.println("</body>");
            out.println("</html>");
        }
    }

    //Intializes PreparedStatements to be used by checkUser() and addToken()
    public void initJDBC() {
        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver").newInstance();
            conn = DriverManager.getConnection(dbURL, dbUser, dbPass);
            conn.setAutoCommit(false);

            ps = conn.prepareStatement("SELECT * FROM users WHERE statecode = ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            addTokenSt = conn.prepareStatement("UPDATE users SET token = ? WHERE email = ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | SQLException ex) {
            ex.printStackTrace();
        }
    }

    //Returns the email of the user with the matching statecode in the database
    public String checkUser(String statecode) {
        String returnString = "not_found";

        try {
            ps.setString(1, statecode);
            ResultSet query = ps.executeQuery();

            if (query.next()) {
                returnString = query.getString("email");
            }

            ps.close();
            query.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        return returnString;
    }

    //Adds the token of the account in the database
    public boolean addToken(String email, String token) {
        try {
            addTokenSt.setString(1, token);
            addTokenSt.setString(2, email);
            if (addTokenSt.executeUpdate() == 0) {
                System.out.println("Error with addUserStateCode");
                return false;
            }

            conn.commit();
            addTokenSt.close();

            return true;
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        return false;
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

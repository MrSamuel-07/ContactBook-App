import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CONTACT BOOK MANAGEMENT SYSTEM
 * Department of Computer Science and Engineering
 * University College of Engineering, Villupuram
 */
public class Main extends JFrame {

    // =========================================================================
    // SECTION 1: DOMAIN OBJECT MODEL (OOP: Encapsulation & Inheritance)
    // =========================================================================
    public static abstract class Contact implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String email;
        private String physicalAddress;
        private LocalDate dateAdded;

        public Contact(String id, String firstName, String lastName, String phoneNumber, 
                       String email, String physicalAddress, LocalDate dateAdded) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.phoneNumber = phoneNumber;
            this.email = email;
            this.physicalAddress = physicalAddress;
            this.dateAdded = dateAdded != null ? dateAdded : LocalDate.now();
        }

        public abstract String getContactType();
        public abstract String getExtraInfo();
        public abstract String toCsvRow();

        public String getId() { return id; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhysicalAddress() { return physicalAddress; }
        public void setPhysicalAddress(String physicalAddress) { this.physicalAddress = physicalAddress; }
        public LocalDate getDateAdded() { return dateAdded; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Contact c)) return false;
            return firstName.equalsIgnoreCase(c.firstName) &&
                   lastName.equalsIgnoreCase(c.lastName) &&
                   phoneNumber.replaceAll("[^0-9]", "").equals(c.phoneNumber.replaceAll("[^0-9]", ""));
        }

        @Override
        public int hashCode() {
            return Objects.hash(firstName.toLowerCase(), lastName.toLowerCase(), phoneNumber.replaceAll("[^0-9]", ""));
        }
    }

    public static class PersonalContact extends Contact {
        private String relationship;

        public PersonalContact(String id, String fn, String ln, String phone, String email, String addr, LocalDate dt, String rel) {
            super(id, fn, ln, phone, email, addr, dt);
            this.relationship = (rel == null || rel.isBlank()) ? "General" : rel;
        }

        public String getRelationship() { return relationship; }
        public void setRelationship(String rel) { this.relationship = rel; }

        @Override public String getContactType() { return "Personal"; }
        @Override public String getExtraInfo() { return "Relation: " + relationship; }
        @Override public String toCsvRow() {
            return String.join(";", "PERSONAL", getId(), getFirstName(), getLastName(), 
                    getPhoneNumber(), getEmail(), getPhysicalAddress(), getDateAdded().toString(), relationship);
        }
    }

    public static class BusinessContact extends Contact {
        private String company;
        private String jobTitle;

        public BusinessContact(String id, String fn, String ln, String phone, String email, String addr, LocalDate dt, String comp, String job) {
            super(id, fn, ln, phone, email, addr, dt);
            this.company = comp;
            this.jobTitle = job;
        }

        public String getCompany() { return company; }
        public String getJobTitle() { return jobTitle; }

        @Override public String getContactType() { return "Business"; }
        @Override public String getExtraInfo() { return company + " (" + jobTitle + ")"; }
        @Override public String toCsvRow() {
            return String.join(";", "BUSINESS", getId(), getFirstName(), getLastName(), 
                    getPhoneNumber(), getEmail(), getPhysicalAddress(), getDateAdded().toString(), company, jobTitle);
        }
    }

    public static class OnlineDirectoryEntry {
        public String name;
        public String phone;
        public String email;
        public String city;
        public String country;

        public OnlineDirectoryEntry(String name, String phone, String email, String city, String country) {
            this.name = name;
            this.phone = phone;
            this.email = email;
            this.city = city;
            this.country = country;
        }
    }

    // =========================================================================
    // SECTION 2: DATA VALIDATION (Regex & Integrity Constraints)
    // =========================================================================
    public static class ValidationUtil {
        private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{1,4}?[-.\\s]?[0-9]{6,14}$");
        private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

        public static void validate(String firstName, String lastName, String phone, String email) throws Exception {
            if (firstName == null || firstName.trim().isEmpty()) {
                throw new Exception("Validation Error: First Name cannot be empty.");
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                throw new Exception("Validation Error: Last Name cannot be empty.");
            }
            if (phone == null || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
                throw new Exception("Invalid Phone Format! Expected: +[CountryCode][Number] or 10-digit number.");
            }
            if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
                throw new Exception("Invalid Email Format! Expected user@domain.com");
            }
        }
    }

    // =========================================================================
    // SECTION 3: OPEN-SOURCE DIRECTORY INTEGRATION & LRU CACHING
    // =========================================================================
    public static class OpenSourceDirectoryService {
        private static final String API_ENDPOINT = "https://randomuser.me/api/?results=25&inc=name,phone,email,location,nat";
        private final HttpClient httpClient;
        private final Map<String, List<OnlineDirectoryEntry>> searchCache = new LinkedHashMap<>(30, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<OnlineDirectoryEntry>> eldest) {
                return size() > 20;
            }
        };

        public OpenSourceDirectoryService() {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .build();
        }

        public List<OnlineDirectoryEntry> searchPublicDirectory(String query, boolean[] wasCached) throws Exception {
            String cleanQuery = query.trim().toLowerCase();
            if (searchCache.containsKey(cleanQuery)) {
                wasCached[0] = true;
                return searchCache.get(cleanQuery);
            }

            wasCached[0] = false;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_ENDPOINT))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new Exception("Directory server returned HTTP status " + response.statusCode());
            }

            List<OnlineDirectoryEntry> entries = parseRandomUserResponse(response.body());
            
            List<OnlineDirectoryEntry> filtered = entries.stream()
                    .filter(e -> cleanQuery.isEmpty() || 
                                 e.name.toLowerCase().contains(cleanQuery) || 
                                 e.phone.contains(cleanQuery) || 
                                 e.email.toLowerCase().contains(cleanQuery))
                    .collect(Collectors.toList());

            searchCache.put(cleanQuery, filtered);
            return filtered;
        }

        private List<OnlineDirectoryEntry> parseRandomUserResponse(String json) {
            List<OnlineDirectoryEntry> list = new ArrayList<>();
            Pattern resultBlockPattern = Pattern.compile("\\{([^\\{\\}]*\"name\"[\\s\\S]*?)(?=\\}\\,\\s*\\{|\\}\\]\\s*\\})");
            Matcher m = resultBlockPattern.matcher(json);

            while (m.find()) {
                String block = m.group(1);
                String first = extractValue(block, "first");
                String last = extractValue(block, "last");
                String phone = extractValue(block, "phone");
                String email = extractValue(block, "email");
                String city = extractValue(block, "city");
                String country = extractValue(block, "country");

                String fullName = (first.equals("N/A") ? "" : first) + " " + (last.equals("N/A") ? "" : last);
                list.add(new OnlineDirectoryEntry(fullName.trim(), phone, email, city, country));
            }
            return list;
        }

        private String extractValue(String block, String key) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(block);
            return m.find() ? m.group(1) : "N/A";
        }
    }

    // =========================================================================
    // SECTION 4: STORAGE & 100 PUBLIC CONTACTS SEED GENERATOR
    // =========================================================================
    public static class StorageService {
        private final String FILE_NAME = "contacts.csv";

        public List<Contact> loadOrSeedContacts() {
            List<Contact> contacts = new ArrayList<>();
            File file = new File(FILE_NAME);

            if (file.exists()) {
                contacts = readFromFile(file);
            }

            if (contacts.isEmpty()) {
                contacts = generate100Contacts();
                saveToFile(contacts, file);
            }
            return contacts;
        }

        public void save(List<Contact> contacts) {
            saveToFile(contacts, new File(FILE_NAME));
        }

        public void saveToFile(List<Contact> contacts, File target) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(target))) {
                for (Contact c : contacts) {
                    bw.write(c.toCsvRow());
                    bw.newLine();
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error persisting contacts: " + e.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        public List<Contact> readFromFile(File target) {
            List<Contact> list = new ArrayList<>();
            if (!target.exists()) return list;

            try (BufferedReader br = new BufferedReader(new FileReader(target))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] p = line.split(";");
                    if (p.length >= 8) {
                        String type = p[0];
                        String id = p[1];
                        String fn = p[2];
                        String ln = p[3];
                        String ph = p[4];
                        String em = p[5];
                        String addr = p[6];
                        LocalDate dt = LocalDate.parse(p[7]);

                        if ("PERSONAL".equalsIgnoreCase(type)) {
                            list.add(new PersonalContact(id, fn, ln, ph, em, addr, dt, p.length > 8 ? p[8] : "General"));
                        } else {
                            list.add(new BusinessContact(id, fn, ln, ph, em, addr, dt, p.length > 8 ? p[8] : "Corporate", p.length > 9 ? p[9] : "Associate"));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("File parse notice: " + e.getMessage());
            }
            return list;
        }

        private List<Contact> generate100Contacts() {
            List<Contact> list = new ArrayList<>(100);
            String[] firstNames = {
                "Aarav", "Diya", "Aditya", "Ananya", "Rahul", "Pooja", "Vikram", "Sneha", "Rohan", "Kavya",
                "Karthik", "Priya", "Sanjay", "Deepa", "Arjun", "Meera", "Varun", "Swati", "Naveen", "Divya",
                "James", "Emma", "Liam", "Olivia", "Noah", "Ava", "William", "Sophia", "Benjamin", "Isabella",
                "Lucas", "Mia", "Henry", "Evelyn", "Alexander", "Harper", "Sebastian", "Camila", "Jack", "Gianna",
                "Suresh", "Lakshmi", "Ganesh", "Revathi", "Murugan", "Bhuvan", "Preethi", "Ashwin", "Pavithra", "Harish"
            };
            String[] lastNames = {
                "Sharma", "Patel", "Verma", "Iyer", "Rao", "Nair", "Reddy", "Chatterjee", "Gupta", "Menon",
                "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Garcia", "Rodriguez", "Wilson",
                "Pillai", "Sundaram", "Subramanian", "Krishnan", "Venkatesh", "Balaji", "Natarajan", "Anand", "Rajesh", "Chawla"
            };
            String[] cities = {
                "Chennai, Tamil Nadu", "Villupuram, Tamil Nadu", "Bengaluru, Karnataka", "Hyderabad, Telangana",
                "Mumbai, Maharashtra", "New Delhi, Delhi", "Kochi, Kerala", "Pune, Maharashtra", "Coimbatore, Tamil Nadu",
                "San Jose, CA, USA", "Austin, TX, USA", "London, UK", "Singapore"
            };
            String[] companies = {
                "Tata Consultancy Services", "Infosys Technologies", "Wipro Digital", "Zoho Corp", "HCL Tech",
                "Google LLC", "Microsoft", "Amazon AWS", "Cognizant Solutions", "Accenture Solutions"
            };
            String[] titles = {"Software Engineer", "Systems Architect", "Data Analyst", "Product Manager", "DevOps Engineer"};
            String[] relations = {"Family", "Close Friend", "College Batchmate", "Neighbor", "Professor", "Acquaintance"};

            Random rng = new Random(42);

            for (int i = 1; i <= 100; i++) {
                String fn = firstNames[rng.nextInt(firstNames.length)];
                String ln = lastNames[rng.nextInt(lastNames.length)];
                String id = "ID" + String.format("%03d", i);
                String email = fn.toLowerCase() + "." + ln.toLowerCase() + i + "@example.com";
                String city = cities[rng.nextInt(cities.length)];
                LocalDate dt = LocalDate.now().minusDays(rng.nextInt(365));

                if (i % 2 == 0) {
                    String phone = "+91 " + (9800000000L + rng.nextInt(199999999));
                    String comp = companies[rng.nextInt(companies.length)];
                    String title = titles[rng.nextInt(titles.length)];
                    list.add(new BusinessContact(id, fn, ln, phone, email, city, dt, comp, title));
                } else {
                    String phone = "+1 " + (2000000000L + rng.nextInt(899999999));
                    String rel = relations[rng.nextInt(relations.length)];
                    list.add(new PersonalContact(id, fn, ln, phone, email, city, dt, rel));
                }
            }
            return list;
        }
    }

    // =========================================================================
    // SECTION 5: GUI PRESENTATION LAYER (Swing Desktop Component)
    // =========================================================================
    private final StorageService storageService = new StorageService();
    private final OpenSourceDirectoryService directoryService = new OpenSourceDirectoryService();
    private final List<Contact> localContacts = new ArrayList<>();
    private List<Contact> viewList = new ArrayList<>();

    // Form inputs
    private JTextField txtId, txtFirstName, txtLastName, txtPhone, txtEmail, txtAddress, txtExtra1, txtExtra2;
    private JComboBox<String> cmbType;
    private JLabel lblExtra1, lblExtra2;
    private JButton btnSave, btnClear, btnDelete, btnExport, btnImport;

    // Table & Pagination
    private DefaultTableModel tableModel;
    private JTable contactTable;
    private JTextField txtSearch;
    private JComboBox<String> cmbSort;
    private JLabel lblPagination;
    private JButton btnPrev, btnNext;

    private int currentPage = 1;
    private final int PAGE_SIZE = 10;

    // Online Directory tab
    private JTextField txtOnlineQuery;
    private DefaultTableModel onlineTableModel;
    private JTable onlineTable;
    private JButton btnOnlineFetch, btnImportDirectory;
    private JLabel lblNetworkStatus;
    private List<OnlineDirectoryEntry> onlineResults = new ArrayList<>();

    public Main() {
        setTitle("Contact Book Management System (2nd Year CSE)");
        setSize(1200, 780);
        setMinimumSize(new Dimension(1000, 650));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        localContacts.addAll(storageService.loadOrSeedContacts());
        viewList.addAll(localContacts);

        initUserInterface();
        applyFilterAndSort();
    }

    private void initUserInterface() {
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        mainTabs.addTab("Local Address Book", createLocalTab());
        mainTabs.addTab("Global Public Directory (Open Source)", createOnlineTab());

        add(mainTabs);
    }

    private JPanel createLocalTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // LEFT FORM PANEL
        JPanel formContainer = new JPanel(new BorderLayout());
        formContainer.setBorder(new TitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Manage Contact Record"));
        formContainer.setPreferredSize(new Dimension(340, 0));

        JPanel grid = new JPanel(new GridLayout(9, 2, 6, 8));
        grid.setBorder(new EmptyBorder(10, 8, 10, 8));

        txtId = new JTextField(); txtId.setEditable(false);
        txtFirstName = new JTextField();
        txtLastName = new JTextField();
        txtPhone = new JTextField();
        txtEmail = new JTextField();
        txtAddress = new JTextField();
        txtExtra1 = new JTextField();
        txtExtra2 = new JTextField();

        lblExtra1 = new JLabel("Relationship:");
        lblExtra2 = new JLabel("Job Title:");
        lblExtra2.setVisible(false);
        txtExtra2.setVisible(false);

        cmbType = new JComboBox<>(new String[]{"Personal", "Business"});
        cmbType.addActionListener(e -> {
            boolean isBusiness = "Business".equals(cmbType.getSelectedItem());
            lblExtra1.setText(isBusiness ? "Company:" : "Relationship:");
            lblExtra2.setVisible(isBusiness);
            txtExtra2.setVisible(isBusiness);
        });

        grid.add(new JLabel("Record ID:")); grid.add(txtId);
        grid.add(new JLabel("Contact Category:")); grid.add(cmbType);
        grid.add(new JLabel("First Name*:")); grid.add(txtFirstName);
        grid.add(new JLabel("Last Name*:")); grid.add(txtLastName);
        grid.add(new JLabel("Phone Number*:")); grid.add(txtPhone);
        grid.add(new JLabel("Email Address*:")); grid.add(txtEmail);
        grid.add(new JLabel("Address/City:")); grid.add(txtAddress);
        grid.add(lblExtra1); grid.add(txtExtra1);
        grid.add(lblExtra2); grid.add(txtExtra2);

        JPanel btnPanel = new JPanel(new GridLayout(3, 2, 6, 6));
        btnSave = new JButton("Save / Update");
        btnClear = new JButton("Clear Input");
        btnDelete = new JButton("Delete Record");
        btnDelete.setForeground(new Color(180, 0, 0));
        btnExport = new JButton("Export to CSV");
        btnImport = new JButton("Import from CSV");
        JButton btnComm = new JButton("Launch Mail");

        btnSave.addActionListener(e -> saveOrUpdate());
        btnClear.addActionListener(e -> clearForm());
        btnDelete.addActionListener(e -> deleteSelected());
        btnExport.addActionListener(e -> exportToCsv());
        btnImport.addActionListener(e -> importFromCsv());
        btnComm.addActionListener(e -> launchDefaultMailer());

        btnPanel.add(btnSave);
        btnPanel.add(btnClear);
        btnPanel.add(btnDelete);
        btnPanel.add(btnComm);
        btnPanel.add(btnExport);
        btnPanel.add(btnImport);

        formContainer.add(grid, BorderLayout.NORTH);
        formContainer.add(btnPanel, BorderLayout.SOUTH);

        // RIGHT WORKSPACE: SEARCH, PAGINATION & TABLE
        JPanel tableContainer = new JPanel(new BorderLayout(8, 8));

        // Filter / Search Toolbar
        JPanel topFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        txtSearch = new JTextField(14);
        JButton btnSearch = new JButton("Filter");
        JButton btnReset = new JButton("Reset");
        cmbSort = new JComboBox<>(new String[]{
            "First Name (A -> Z)",
            "Last Name (A -> Z)",
            "Date Added (Newest)",
            "Date Added (Oldest)",
            "Category (Personal -> Business)"
        });

        btnSearch.addActionListener(e -> applyFilterAndSort());
        btnReset.addActionListener(e -> {
            txtSearch.setText("");
            cmbSort.setSelectedIndex(0);
            applyFilterAndSort();
        });
        cmbSort.addActionListener(e -> applyFilterAndSort());

        topFilter.add(new JLabel("Search Filter:"));
        topFilter.add(txtSearch);
        topFilter.add(btnSearch);
        topFilter.add(btnReset);
        topFilter.add(new JLabel("Sort By:"));
        topFilter.add(cmbSort);

        // Table Model
        String[] columns = {"ID", "Type", "Full Name", "Phone Number", "Email Address", "Address", "Date Added", "Specific Details"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        contactTable = new JTable(tableModel);
        contactTable.setRowHeight(24);
        contactTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contactTable.getSelectionModel().addListSelectionListener(e -> fillFormFromSelectedRow());
        JScrollPane scrollPane = new JScrollPane(contactTable);

        // Pagination Bar
        JPanel paginationBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        lblPagination = new JLabel("Page 1 of 1");
        btnPrev = new JButton("< Previous");
        btnNext = new JButton("Next >");

        btnPrev.addActionListener(e -> {
            if (currentPage > 1) { currentPage--; renderTableRows(); }
        });
        btnNext.addActionListener(e -> {
            int maxPages = Math.max(1, (int) Math.ceil((double) viewList.size() / PAGE_SIZE));
            if (currentPage < maxPages) { currentPage++; renderTableRows(); }
        });

        paginationBar.add(lblPagination);
        paginationBar.add(btnPrev);
        paginationBar.add(btnNext);

        tableContainer.add(topFilter, BorderLayout.NORTH);
        tableContainer.add(scrollPane, BorderLayout.CENTER);
        tableContainer.add(paginationBar, BorderLayout.SOUTH);

        root.add(formContainer, BorderLayout.WEST);
        root.add(tableContainer, BorderLayout.CENTER);

        return root;
    }

    private JPanel createOnlineTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        txtOnlineQuery = new JTextField(18);
        btnOnlineFetch = new JButton("Search RandomUser.me Directory");
        btnImportDirectory = new JButton("Import Contact into Local Address Book");
        lblNetworkStatus = new JLabel("Endpoint: api.randomuser.me | Status: Ready");
        lblNetworkStatus.setForeground(new Color(0, 102, 204));

        topBar.add(new JLabel("Name/Phone Filter:"));
        topBar.add(txtOnlineQuery);
        topBar.add(btnOnlineFetch);
        topBar.add(btnImportDirectory);
        topBar.add(lblNetworkStatus);

        String[] cols = {"Name", "Phone", "Email", "City", "Country / NAT"};
        onlineTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        onlineTable = new JTable(onlineTableModel);
        onlineTable.setRowHeight(24);
        onlineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        btnOnlineFetch.addActionListener(e -> performOnlineDirectorySearch());
        btnImportDirectory.addActionListener(e -> importSelectedOnlineRecord());

        root.add(topBar, BorderLayout.NORTH);
        root.add(new JScrollPane(onlineTable), BorderLayout.CENTER);

        return root;
    }

    // =========================================================================
    // SECTION 6: ACTIONS, CONTROLLERS & EVENT LISTENERS
    // =========================================================================
    private void saveOrUpdate() {
        try {
            String id = txtId.getText().trim();
            String fn = txtFirstName.getText().trim();
            String ln = txtLastName.getText().trim();
            String phone = txtPhone.getText().trim();
            String email = txtEmail.getText().trim();
            String addr = txtAddress.getText().trim();
            String extra1 = txtExtra1.getText().trim();
            String extra2 = txtExtra2.getText().trim();
            boolean isBusiness = "Business".equals(cmbType.getSelectedItem());

            ValidationUtil.validate(fn, ln, phone, email);

            if (id.isEmpty()) {
                String newId = "ID" + String.format("%03d", localContacts.size() + 1);
                Contact c = isBusiness ?
                        new BusinessContact(newId, fn, ln, phone, email, addr, LocalDate.now(), extra1, extra2) :
                        new PersonalContact(newId, fn, ln, phone, email, addr, LocalDate.now(), extra1);

                if (localContacts.contains(c)) {
                    JOptionPane.showMessageDialog(this, "Duplicate Warning: A contact with the same Name and Phone already exists!", "Integrity Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                localContacts.add(c);
                JOptionPane.showMessageDialog(this, "Contact added successfully with ID: " + newId);
            } else {
                Contact existing = localContacts.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
                if (existing != null) {
                    existing.setFirstName(fn);
                    existing.setLastName(ln);
                    existing.setPhoneNumber(phone);
                    existing.setEmail(email);
                    existing.setPhysicalAddress(addr);
                    if (existing instanceof PersonalContact pc) pc.setRelationship(extra1);
                    JOptionPane.showMessageDialog(this, "Contact details updated.");
                }
            }

            storageService.save(localContacts);
            clearForm();
            applyFilterAndSort();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Input Validation", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        String id = txtId.getText().trim();
        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a contact from the table to delete.");
            return;
        }

        int resp = JOptionPane.showConfirmDialog(this, "Permanently delete contact ID " + id + "?", "Confirm Action", JOptionPane.YES_NO_OPTION);
        if (resp == JOptionPane.YES_OPTION) {
            localContacts.removeIf(c -> c.getId().equalsIgnoreCase(id));
            storageService.save(localContacts);
            clearForm();
            applyFilterAndSort();
            JOptionPane.showMessageDialog(this, "Record deleted.");
        }
    }

    private void clearForm() {
        txtId.setText("");
        txtFirstName.setText("");
        txtLastName.setText("");
        txtPhone.setText("");
        txtEmail.setText("");
        txtAddress.setText("");
        txtExtra1.setText("");
        txtExtra2.setText("");
        contactTable.clearSelection();
    }

    private void fillFormFromSelectedRow() {
        int r = contactTable.getSelectedRow();
        if (r >= 0) {
            String id = (String) tableModel.getValueAt(r, 0);
            Contact c = localContacts.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            if (c != null) {
                txtId.setText(c.getId());
                txtFirstName.setText(c.getFirstName());
                txtLastName.setText(c.getLastName());
                txtPhone.setText(c.getPhoneNumber());
                txtEmail.setText(c.getEmail());
                txtAddress.setText(c.getPhysicalAddress());
                if (c instanceof PersonalContact pc) {
                    cmbType.setSelectedItem("Personal");
                    txtExtra1.setText(pc.getRelationship());
                } else if (c instanceof BusinessContact bc) {
                    cmbType.setSelectedItem("Business");
                    txtExtra1.setText(bc.getCompany());
                    txtExtra2.setText(bc.getJobTitle());
                }
            }
        }
    }

    private void applyFilterAndSort() {
        String q = txtSearch.getText().trim().toLowerCase();
        viewList = localContacts.stream().filter(c ->
                q.isEmpty() ||
                c.getFirstName().toLowerCase().contains(q) ||
                c.getLastName().toLowerCase().contains(q) ||
                c.getPhoneNumber().contains(q) ||
                c.getEmail().toLowerCase().contains(q) ||
                c.getPhysicalAddress().toLowerCase().contains(q)
        ).collect(Collectors.toList());

        switch (cmbSort.getSelectedIndex()) {
            case 0 -> viewList.sort(Comparator.comparing(Contact::getFirstName, String.CASE_INSENSITIVE_ORDER));
            case 1 -> viewList.sort(Comparator.comparing(Contact::getLastName, String.CASE_INSENSITIVE_ORDER));
            case 2 -> viewList.sort(Comparator.comparing(Contact::getDateAdded).reversed());
            case 3 -> viewList.sort(Comparator.comparing(Contact::getDateAdded));
            case 4 -> viewList.sort(Comparator.comparing(Contact::getContactType));
        }

        currentPage = 1;
        renderTableRows();
    }

    private void renderTableRows() {
        tableModel.setRowCount(0);
        int total = viewList.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));

        if (currentPage > maxPages) currentPage = maxPages;

        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);

        for (int i = from; i < to; i++) {
            Contact c = viewList.get(i);
            tableModel.addRow(new Object[]{
                    c.getId(),
                    c.getContactType(),
                    c.getFirstName() + " " + c.getLastName(),
                    c.getPhoneNumber(),
                    c.getEmail(),
                    c.getPhysicalAddress(),
                    c.getDateAdded().toString(),
                    c.getExtraInfo()
            });
        }

        lblPagination.setText(String.format("Page %d of %d (Showing %d-%d of %d Contacts)", 
                currentPage, maxPages, total == 0 ? 0 : from + 1, to, total));
        btnPrev.setEnabled(currentPage > 1);
        btnNext.setEnabled(currentPage < maxPages);
    }

    private void performOnlineDirectorySearch() {
        String q = txtOnlineQuery.getText().trim();
        btnOnlineFetch.setEnabled(false);
        lblNetworkStatus.setText("Fetching from RandomUser Open API...");

        new Thread(() -> {
            boolean[] wasCached = new boolean[1];
            try {
                onlineResults = directoryService.searchPublicDirectory(q, wasCached);
                SwingUtilities.invokeLater(() -> {
                    onlineTableModel.setRowCount(0);
                    for (OnlineDirectoryEntry ode : onlineResults) {
                        onlineTableModel.addRow(new Object[]{ode.name, ode.phone, ode.email, ode.city, ode.country});
                    }
                    lblNetworkStatus.setText(wasCached[0] ? "Loaded from In-Memory Cache." : "Fetched live from RandomUser.me API.");
                    if (onlineResults.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "No records matched your search in the open directory.");
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblNetworkStatus.setText("Network/API Offline.");
                    JOptionPane.showMessageDialog(this, "Network directory notice: " + ex.getMessage(), "Connection Info", JOptionPane.WARNING_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> btnOnlineFetch.setEnabled(true));
            }
        }).start();
    }

    private void importSelectedOnlineRecord() {
        int r = onlineTable.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a record from the online directory table to import.");
            return;
        }

        OnlineDirectoryEntry item = onlineResults.get(r);
        String[] parts = item.name.split(" ", 2);
        String fn = parts[0];
        String ln = parts.length > 1 ? parts[1] : "Public";
        String id = "ID" + String.format("%03d", localContacts.size() + 1);

        Contact imported = new BusinessContact(id, fn, ln, item.phone, item.email, item.city + ", " + item.country, LocalDate.now(), "Public Directory", "Online Contact");

        if (localContacts.contains(imported)) {
            JOptionPane.showMessageDialog(this, "Contact already exists in local storage.");
            return;
        }

        localContacts.add(imported);
        storageService.save(localContacts);
        applyFilterAndSort();
        JOptionPane.showMessageDialog(this, "Imported " + item.name + " to Local Contacts!");
    }

    private void exportToCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("contacts_backup.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            storageService.saveToFile(localContacts, fc.getSelectedFile());
            JOptionPane.showMessageDialog(this, "Contacts successfully exported to: " + fc.getSelectedFile().getName());
        }
    }

    private void importFromCsv() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            List<Contact> imported = storageService.readFromFile(fc.getSelectedFile());
            int added = 0;
            for (Contact c : imported) {
                if (!localContacts.contains(c)) {
                    localContacts.add(c);
                    added++;
                }
            }
            storageService.save(localContacts);
            applyFilterAndSort();
            JOptionPane.showMessageDialog(this, "Imported " + added + " new contacts from file.");
        }
    }

    private void launchDefaultMailer() {
        String email = txtEmail.getText().trim();
        if (email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select or enter an email address first.");
            return;
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            try {
                Desktop.getDesktop().mail(new URI("mailto:" + email));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Unable to launch system mail client: " + e.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(this, "Desktop mail action not supported on this platform.");
        }
    }

    // =========================================================================
    // SECTION 7: APPLICATION ENTRY POINT
    // =========================================================================
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            Main app = new Main();
            app.setVisible(true);
        });
    }
}
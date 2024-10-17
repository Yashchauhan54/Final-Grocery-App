package com.example.Cart_grocery;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/")
public class HomeController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

    @Autowired
    private ProductService productService;

    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");

        List<Product> products = productService.getAllProducts();
        List<Item> cartItems = (List<Item>) session.getAttribute("cartItems");
        if (cartItems == null) {
            cartItems = new ArrayList<>();
        }
        model.addAttribute("user", user);
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartQty", cartItems.size());
        model.addAttribute("total", calculateTotal(cartItems));
        model.addAttribute("products", products);
        return "home";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam String item, @RequestParam String image, @RequestParam double price, HttpSession session) {
        List<Item> cartItems = (List<Item>) session.getAttribute("cartItems");
        if (cartItems == null) {
            cartItems = new ArrayList<>();
            session.setAttribute("cartItems", cartItems);
        }

        boolean itemExists = false;
        for (Item cartItem : cartItems) {
            if (cartItem.getName().equals(item)) {
                cartItem.setQuantity(cartItem.getQuantity() + 1);
                itemExists = true;
                break;
            }
        }

        if (!itemExists) {
            cartItems.add(new Item(item, image, price));
        }

        return "redirect:/";
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam int index, HttpSession session) {
        List<Item> cartItems = (List<Item>) session.getAttribute("cartItems");
        if (cartItems != null && index >= 0 && index < cartItems.size()) {
            cartItems.remove(index);
        }
        return "redirect:/cart";
    }

    private double calculateTotal(List<Item> cartItems) {
        double total = 0;
        for (Item item : cartItems) {
            total += item.getTotalAmount();
        }
        return total;
    }

    @GetMapping("/cart")
    public String cart(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");

        List<Item> cartItems = (List<Item>) session.getAttribute("cartItems");
        if (cartItems == null) {
            cartItems = new ArrayList<>();
        }
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartQty", cartItems.size());

        double totalAmount = calculateTotal(cartItems);
        DecimalFormat df = new DecimalFormat("#0.00");
        String formattedTotal = df.format(totalAmount);

        model.addAttribute("total", formattedTotal);

        return "cart";
    }

    @GetMapping("/signup")
    public String signupForm() {
        return "signup";
    }

    @PostMapping("/signup-v")
    public String signup(@ModelAttribute("user") User user, @RequestParam String name, @RequestParam String email,
                         @RequestParam String password, @RequestParam String verifyPassword,
                         Model model) {
        if (!password.equals(verifyPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match");
            return "signup";
        }

        // Step 1: Check if any field is empty
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            model.addAttribute("errorMessage", "All fields are required");
            return "signup"; // Return the signup page with an error message
        }

        // Step 2: Validate email format (simple regex)
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            model.addAttribute("errorMessage", "Invalid email format");
            return "signup";
        }

        // Step 3: Validate password length
        if (password.length() < 6) {
            model.addAttribute("errorMessage", "Password must be at least 6 characters long");
            return "signup";
        }

        User existingUser = userRepository.findByEmail(email);
        if (existingUser != null) {
            model.addAttribute("errorMessage", "Email is already registered");
            return "signup";
        }

        User newUser = new User();
        newUser.setName(name);
        newUser.setEmail(email);
        newUser.setPassword(password); // Ensure password is hashed before saving in production
        newUser.setType("user"); // Set type to "user"

        userRepository.save(newUser);

        model.addAttribute("successMessage", "User created successfully");
        return "signup";
    }

    @GetMapping("/login")
    public String index() {
        return "login";
    }

    @PostMapping("/login-v")
    public String login(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        User user = userRepository.findByEmail(email);
        if (user != null && user.getPassword().equals(password)) { // Password should be hashed and compared
            session.setAttribute("user", user);

            if ("admin".equals(user.getType())) {
                return "redirect:/admin/products";
            } else if ("user".equals(user.getType())) {
                return "redirect:/dashboard";
            }
        } else {
            model.addAttribute("error", "Invalid email or password");
            return "login";
        }
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/order")
    public String order(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        // Retrieve cart items from session
        List<Item> cartItems = (List<Item>) session.getAttribute("cartItems");
        if (cartItems == null || cartItems.isEmpty()) {
            return "redirect:/cart"; // If no items in cart, redirect to cart
        }

        // Calculate total amount
        double totalAmount = calculateTotal(cartItems);

        // Create new order
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(totalAmount);
        order.setOrderDate(new Date());
        order.setOrderHistoryList(new ArrayList<>());

        // Save order to DB first (so we get an order ID)
        Order savedOrder = orderRepository.save(order);

        // Create order history for each cart item
        for (Item item : cartItems) {
            OrderHistory orderHistory = new OrderHistory();
            orderHistory.setOrder(savedOrder);
            orderHistory.setProductName(item.getName());
            orderHistory.setProductImage(item.getImage());
            orderHistory.setProductPrice(item.getPrice());
            orderHistory.setQuantity(item.getQuantity());

            // Save each order history to DB
            orderHistoryRepository.save(orderHistory);
        }

        // Clear the cart
        session.removeAttribute("cartItems");

        // Redirect to success page
        return "redirect:/success";
    }

    @GetMapping("/success")
    public String success(HttpSession session, Model model) {
        return "success";
    }

    @GetMapping("/my-orders")
    public String viewOrders(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        List<Order> orders = orderRepository.findByUserOrderByOrderDateDesc(user);
        model.addAttribute("orders", orders);
        return "order-history"; // Create a view for displaying orders
    }

    @GetMapping("/order-details/{orderId}")
    public String orderDetails(@PathVariable Long orderId, Model model) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Invalid order Id:" + orderId));
        List<OrderHistory> orderHistoryList = orderHistoryRepository.findByOrder(order);

        model.addAttribute("orderHistoryList", orderHistoryList);
        model.addAttribute("order", order);
        return "order-details";
    }
}

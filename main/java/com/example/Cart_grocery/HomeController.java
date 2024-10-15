package com.example.Cart_grocery;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/")
public class HomeController {

    @Autowired
    private UserRepository userRepository;

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

        model.addAttribute("total",formattedTotal);


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
}


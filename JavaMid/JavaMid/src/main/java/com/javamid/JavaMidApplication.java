package com.javamid;

import com.javamid.ui.WeatherMapWindow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

@SpringBootApplication
public class JavaMidApplication {
    public static void main(String[] args) {
        boolean forceServer = Arrays.stream(args)
                .anyMatch(a -> "--server".equalsIgnoreCase(a) || "--spring".equalsIgnoreCase(a));

        if (GraphicsEnvironment.isHeadless() || forceServer) {
            SpringApplication.run(JavaMidApplication.class, args);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            WeatherMapWindow window = new WeatherMapWindow();
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}


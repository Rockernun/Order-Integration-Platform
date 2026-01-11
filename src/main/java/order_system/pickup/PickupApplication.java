package order_system.pickup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PickupApplication {

	public static void main(String[] args) {
		SpringApplication.run(PickupApplication.class, args);
	}

}

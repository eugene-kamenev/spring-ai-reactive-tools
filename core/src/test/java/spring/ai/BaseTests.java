package spring.ai;

import java.util.List;
import java.util.function.Function;

import reactor.core.publisher.Mono;

public class BaseTests {
	enum Gender {
		MALE, FEMALE
	}

	enum DegreeType {
		HIGH_SCHOOL, ASSOCIATES, BACHELORS, MASTERS, PHD
	}


	record Address(String street, String city, String state, String zipCode) {
	}

	record Education(DegreeType degreeType, String fieldOfStudy, String university, int graduationYear) {
	}

	record Person(String name, int age, Gender gender, Address address, List<Education> educationHistory) {
	}

	protected static Function<String, Mono<String>> EXECUTE_COMMAND = (command) -> {
		if (command == null || command.isBlank()) {
					return Mono.error(new IllegalArgumentException("Command cannot be null or empty"));
				}
				return Mono.just(">" + command + "\n Result: \n" +
						"total 0\n" +
						"drwxr-xr-x  2 root root 4096 Oct 10 12:00 .\n" +
						"drwxr-xr-x  3 root root 4096 Oct 10 12:00 ..\n" +
						"drwxr-xr-x  2 root root 4096 Oct 10 12:00 .git\n");
	};
}

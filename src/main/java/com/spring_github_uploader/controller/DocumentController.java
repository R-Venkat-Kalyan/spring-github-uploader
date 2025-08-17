package com.spring_github_uploader.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class DocumentController {

	@Value("${github.token}")
	private String githubToken;

	@Value("${github.repo.name}")
	private String githubRepo;

	@Value("${github.repo.owner}")
	private String githubOwner;

	// Upload form
	@GetMapping("/")
	public String uploadForm() {
		return "upload"; // uploadForm.html in templates
	}

	// Handle file upload
	@PostMapping("/upload")
	public String uploadResume(@RequestParam("file") MultipartFile file, Model model) throws IOException {
		if (file.isEmpty()) {
			model.addAttribute("message", "Please select a file to upload.");
			return "error";
		}

		// raw file name
		String fileName = file.getOriginalFilename();

		// Encode only for GitHub API
		String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

		String githubApiUrl = "https://api.github.com/repos/" + githubOwner + "/" + githubRepo + "/contents/"
				+ encodedFileName;

		// Convert file to Base64
		String base64Content = Base64.getEncoder().encodeToString(file.getBytes());

		// JSON payload
		Map<String, Object> body = new HashMap<>();
		body.put("message", "JobTrackrly: Resume Upload " + fileName);
		body.put("content", base64Content);

		// Request headers
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(githubToken);

		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

		// Send request
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.exchange(githubApiUrl, HttpMethod.PUT, entity, String.class);

		if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
			model.addAttribute("message", "✅ Resume uploaded successfully!");
			model.addAttribute("fileName", fileName); // keep raw filename for view
			return "success"; // success.html
		} else {
			model.addAttribute("message", "❌ Failed to upload resume.");
			model.addAttribute("error", response.getBody());
			return "error"; // error.html
		}
	}

	@GetMapping("/view/{fileName}")
	public ResponseEntity<byte[]> viewResume(@PathVariable String fileName) throws IOException {
	    // Encode only before GitHub API
	    String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

	    String githubApiUrl = "https://api.github.com/repos/" + githubOwner + "/" + githubRepo + "/contents/"
	            + encodedFileName;

	    HttpHeaders headers = new HttpHeaders();
	    headers.setBearerAuth(githubToken);

	    RestTemplate restTemplate = new RestTemplate();
	    ResponseEntity<Map> response = restTemplate.exchange(
	            githubApiUrl,
	            HttpMethod.GET,
	            new HttpEntity<>(headers),
	            Map.class
	    );

	    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
	        String base64Content = (String) response.getBody().get("content");

	        // ✅ Clean Base64 string: remove newlines and spaces
	        base64Content = base64Content.replaceAll("\\s+", "");

	        byte[] fileBytes = Base64.getDecoder().decode(base64Content);

	        // Detect content type
	        String contentType;
	        String disposition;
	        if (fileName.toLowerCase().endsWith(".pdf")) {
	            contentType = "application/pdf";
	            disposition = "inline"; // show in browser
	        } else {
	            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
	            disposition = "attachment"; // download DOCX
	        }

	        HttpHeaders responseHeaders = new HttpHeaders();
	        responseHeaders.setContentType(MediaType.parseMediaType(contentType));
	        responseHeaders.setContentDispositionFormData(disposition, fileName);

	        return new ResponseEntity<>(fileBytes, responseHeaders, HttpStatus.OK);
	    }

	    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
	}


}

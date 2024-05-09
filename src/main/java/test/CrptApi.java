package test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final TokenBucket tokenBucket;
    private final CrptAdapter crptAdapter;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.tokenBucket = new TokenBucket(requestLimit, timeUnit);// тут реализована работа с многопоточным доступом и с получением возможзности на выполнение запроса
        this.crptAdapter = new CrptAdapter(); // вынесен отдельно для удобства, чтобы в одном месте не было и организации доступа, и самого вызова.
    }

    // единственный публичный метод, который будет вызываться из других классов для создания документа
    public void createDocumentForNewRussianProduct(Document document, String signature) {
        while (!tokenBucket.hasToken()) { // ждем пока освободится токен под новый запрос
            System.out.println("awaiting token");
        }
        try {
            crptAdapter.createDocument(document, signature); // выполняем запрос
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("oops, unexpected error"); // при непредвиденных ошибках
        }
    }

    private class CrptAdapter {
        private static final String ENDPOINT = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        private static final String HTTP_METHOD_POST = "POST";
        private static final TrustManager MOCK_TRUST_MANAGER = getMockTrustManager();

        public void createDocument(Document document, String signature) throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
            var sslContext = SSLContext.getInstance("SSL");// так как подключение по https, нужен будет SSL
            sslContext.init(null, new TrustManager[]{MOCK_TRUST_MANAGER}, new SecureRandom());
            HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();

            var message = prepareMessage(document, signature); // тут будет маппинг с объекта документа на ДТО

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .method(HTTP_METHOD_POST, HttpRequest.BodyPublishers.ofString(message))
                    .build();

            var respHandler = HttpResponse.BodyHandlers.ofString();
            var response = client.send(request, respHandler);

            System.out.printf("Response message: \n%s%n", response.body()); // вывод ответа
        }

        private String prepareMessage(Document document, String signature) {
            try {
                document.getProducts()
                        .forEach(product -> product.setCertificate_document(signature));
                return new ObjectMapper().writeValueAsString(document);
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("not valid document");
            }
        }
    }

    private class TokenBucket {
        private final int bucketSize;
        private final AtomicInteger availableTokens;

        public TokenBucket(int bucketSize, TimeUnit timeUnitForPeriod) {
            this.bucketSize = bucketSize;
            this.availableTokens = new AtomicInteger(bucketSize);
            // задаем промежуток, в который будет появляться новый пул доступных токенов для запросов
            Executors.newSingleThreadScheduledExecutor()
                    .scheduleAtFixedRate(this::updateTokens, 0, 1, timeUnitForPeriod);
        }

        public boolean hasToken() {
            if (availableTokens.get() == 0)
                return false;

            availableTokens.decrementAndGet();
            return true;
        }

        private void updateTokens() {
            System.out.println("Start update tokens");
            availableTokens.set(bucketSize);
            System.out.println("Tokens updated successfully");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private String importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        public Document() {
        }

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDoc_id() {
            return doc_id;
        }

        public void setDoc_id(String doc_id) {
            this.doc_id = doc_id;
        }

        public String getDoc_status() {
            return doc_status;
        }

        public void setDoc_status(String doc_status) {
            this.doc_status = doc_status;
        }

        public String getDoc_type() {
            return doc_type;
        }

        public void setDoc_type(String doc_type) {
            this.doc_type = doc_type;
        }

        public String getImportRequest() {
            return importRequest;
        }

        public void setImportRequest(String importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public String getParticipant_inn() {
            return participant_inn;
        }

        public void setParticipant_inn(String participant_inn) {
            this.participant_inn = participant_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public String getProduction_date() {
            return production_date;
        }

        public void setProduction_date(String production_date) {
            this.production_date = production_date;
        }

        public String getProduction_type() {
            return production_type;
        }

        public void setProduction_type(String production_type) {
            this.production_type = production_type;
        }

        public List<Product> getProducts() {
            return products;
        }

        public void setProducts(List<Product> products) {
            this.products = products;
        }

        public String getReg_date() {
            return reg_date;
        }

        public void setReg_date(String reg_date) {
            this.reg_date = reg_date;
        }

        public String getReg_number() {
            return reg_number;
        }

        public void setReg_number(String reg_number) {
            this.reg_number = reg_number;
        }

        public Document(Description description, String doc_id, String doc_status, String doc_type, String importRequest, String owner_inn, String participant_inn, String producer_inn, String production_date, String production_type, List<Product> products, String reg_date, String reg_number) {
            this.description = description;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Product {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;

            public Product() {
            }

            public String getCertificate_document() {
                return certificate_document;
            }

            public void setCertificate_document(String certificate_document) {
                this.certificate_document = certificate_document;
            }

            public String getCertificate_document_date() {
                return certificate_document_date;
            }

            public void setCertificate_document_date(String certificate_document_date) {
                this.certificate_document_date = certificate_document_date;
            }

            public String getCertificate_document_number() {
                return certificate_document_number;
            }

            public void setCertificate_document_number(String certificate_document_number) {
                this.certificate_document_number = certificate_document_number;
            }

            public String getOwner_inn() {
                return owner_inn;
            }

            public void setOwner_inn(String owner_inn) {
                this.owner_inn = owner_inn;
            }

            public String getProducer_inn() {
                return producer_inn;
            }

            public void setProducer_inn(String producer_inn) {
                this.producer_inn = producer_inn;
            }

            public String getProduction_date() {
                return production_date;
            }

            public void setProduction_date(String production_date) {
                this.production_date = production_date;
            }

            public String getTnved_code() {
                return tnved_code;
            }

            public void setTnved_code(String tnved_code) {
                this.tnved_code = tnved_code;
            }

            public String getUit_code() {
                return uit_code;
            }

            public void setUit_code(String uit_code) {
                this.uit_code = uit_code;
            }

            public String getUitu_code() {
                return uitu_code;
            }

            public void setUitu_code(String uitu_code) {
                this.uitu_code = uitu_code;
            }

            public Product(String certificate_document, String certificate_document_date, String certificate_document_number, String owner_inn, String producer_inn, String production_date, String tnved_code, String uit_code, String uitu_code) {
                this.certificate_document = certificate_document;
                this.certificate_document_date = certificate_document_date;
                this.certificate_document_number = certificate_document_number;
                this.owner_inn = owner_inn;
                this.producer_inn = producer_inn;
                this.production_date = production_date;
                this.tnved_code = tnved_code;
                this.uit_code = uit_code;
                this.uitu_code = uitu_code;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Description {
            private String participantInn;

            public String getParticipantInn() {
                return participantInn;
            }

            public void setParticipantInn(String participantInn) {
                this.participantInn = participantInn;
            }

            public Description() {
            }

            public Description(String participantInn) {
                this.participantInn = participantInn;
            }
        }
    }

    private static TrustManager getMockTrustManager() {
        return new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {

            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
            }
        };
    }
}

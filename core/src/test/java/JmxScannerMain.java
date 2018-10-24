import io.amient.kafka.metrics.JMXScanner;
import io.amient.kafka.metrics.MeasurementPublisher;
import io.amient.kafka.metrics.MeasurementV1;
import org.apache.kafka.clients.consumer.internals.ConsumerMetrics;

import javax.management.MalformedObjectNameException;
import java.io.FileInputStream;
import java.io.IOException;

public class JmxScannerMain {
    public static void main(String[] args) {
        try {
            java.util.Properties props = new java.util.Properties();
            if (args.length == 0) {
                props.load(System.in);
            } else {
                props.load(new FileInputStream(args[0]));
            }
            props.list(System.out);
            MeasurementPublisher publisher = new MeasurementPublisher() {

                @Override
                public void publish(MeasurementV1 m) {
                    System.out.println(m);
                }

                @Override
                public void close() {

                }
            };
            JMXScanner jmxScannerInstance = new JMXScanner(props, publisher);
            ConsumerMetrics consumer = null;
            while (!jmxScannerInstance.isTerminated()) {
                Thread.sleep(5000);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();
        }
    }
}

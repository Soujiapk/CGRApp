package es.hol.cgrapp.cooperativaguarairarepano;

import android.app.ActionBar;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button mDriver, mCustomer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //creando findview by id para los buttons
        mDriver = (Button) findViewById(R.id.driver);
        mCustomer= (Button) findViewById(R.id.customer);

        //al darle lick lo llevara al login de clientes :) so we gucci on thisfre
        mDriver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DriverLoginActivity.class); // we must create this activity!
                startActivity(intent);
                finish();
                return;
            }
        });

        mCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CustomerLoginActivity.class); // we must create this activity!
                startActivity(intent);
                finish();
                return;
            }
        });
    }
}

package org.odk.collect.android.injection.config;

import android.app.Application;
import android.content.Context;
import android.telephony.SmsManager;

import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.injection.ViewModelBuilder;
import org.odk.collect.android.injection.config.architecture.ViewModelFactoryModule;
import org.odk.collect.android.injection.config.scopes.PerApplication;
import org.odk.collect.android.tasks.sms.SmsSubmissionManagerImpl;
import org.odk.collect.android.tasks.sms.contracts.SmsSubmissionManagerContract;
import org.odk.collect.android.utilities.AgingCredentialsProvider;
import org.opendatakit.httpclientandroidlib.client.CookieStore;
import org.opendatakit.httpclientandroidlib.client.CredentialsProvider;
import org.opendatakit.httpclientandroidlib.impl.client.BasicCookieStore;

import dagger.Module;
import dagger.Provides;

/**
 * Add Application level providers here, i.e. if you want to
 * inject something into the Collect instance.
 */
@Module(includes = {ViewModelFactoryModule.class, ViewModelBuilder.class})
public class AppModule {

    @Provides
    SmsManager provideSmsManager() {
        return SmsManager.getDefault();
    }

    @Provides
    SmsSubmissionManagerContract provideSmsSubmissionManager(Application application) {
        return new SmsSubmissionManagerImpl(application);
    }

    @Provides
    Context context(Application application) {
        return application;
    }

    @Provides
    InstancesDao provideInstancesDao() {
        return new InstancesDao();
    }

    @PerApplication
    @Provides
    CredentialsProvider provideCredentialsProvider() {
        // retain credentials for 7 minutes...
        return new AgingCredentialsProvider(7 * 60 * 1000);
    }

    @PerApplication
    @Provides
    CookieStore provideCookieStore() {
        // share all session cookies across all sessions.
        return new BasicCookieStore();
    }
}

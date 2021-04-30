package app.nexusforms.android.feature.smoke;

import android.Manifest;
import android.webkit.MimeTypeMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import app.nexusforms.android.injection.config.AppDependencyModule;
import app.nexusforms.android.openrosa.OpenRosaHttpInterface;
import app.nexusforms.android.support.CollectTestRule;
import app.nexusforms.android.support.ResetStateRule;
import app.nexusforms.android.support.StubOpenRosaServer;
import app.nexusforms.android.support.pages.MainMenuPage;
import app.nexusforms.utilities.UserAgentProvider;

@RunWith(AndroidJUnit4.class)
public class GetAndSubmitFormTest {

    public final StubOpenRosaServer server = new StubOpenRosaServer();

    public CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain copyFormChain = RuleChain
            .outerRule(GrantPermissionRule.grant(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.GET_ACCOUNTS
            ))
            .around(new ResetStateRule(new AppDependencyModule() {
                @Override
                public OpenRosaHttpInterface provideHttpInterface(MimeTypeMap mimeTypeMap, UserAgentProvider userAgentProvider) {
                    return server;
                }
            }))
            .around(rule);

    @Test
    public void canGetBlankForm_fillItIn_andSubmit() {
        server.addForm("One Question", "one-question", "1", "one-question.xml");

        rule.mainMenu()
                .setServer(server.getURL())
                .clickGetBlankForm()
                .clickGetSelected()
                .assertText("One Question (Version:: 1 ID: one-question) - Success")
                .clickOK(new MainMenuPage(rule))

                .startBlankForm("One Question")
                .swipeToEndScreen()
                .clickSaveAndExit()

                .clickSendFinalizedForm(1)
                .clickOnForm("One Question")
                .clickSendSelected()
                .assertText("One Question - Success");
    }
}
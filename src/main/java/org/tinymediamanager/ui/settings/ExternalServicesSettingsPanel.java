/*
 * Copyright 2012 - 2026 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.ui.settings;

import static org.tinymediamanager.ui.TmmFontHelper.H3;
import static org.tinymediamanager.ui.TmmFontHelper.L2;

import java.awt.BorderLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.DateField;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.license.License;
import org.tinymediamanager.thirdparty.simkl.Simkl;
import org.tinymediamanager.thirdparty.trakttv.TraktTv;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.label.LinkLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextArea;
import org.tinymediamanager.ui.components.toast.TmmToastManager;

import net.miginfocom.swing.MigLayout;

/**
 * The class ExternalServicesSettingsPanel. Handle all settings for the external services
 * 
 * @author Manuel Laggner
 */
class ExternalServicesSettingsPanel extends JPanel {
  private static final Logger LOGGER   = LoggerFactory.getLogger(ExternalServicesSettingsPanel.class);

  private final Settings      settings = Settings.getInstance();

  private JButton             btnGetTraktPin;
  private JButton             btnTestTraktConnection;
  private JLabel              lblTraktStatus;
  private JTextField          tfMdbListApiKey;
  private JComboBox           cbTraktDate;

  private JButton             btnGetSimklPin;
  private JButton             btnTestSimklConnection;
  private JLabel              lblSimklStatus;

  ExternalServicesSettingsPanel() {
    // UI init
    initComponents();

    // data init
    if (License.getInstance().isValidLicense()
        && StringUtils.isNoneBlank(TraktTv.getInstance().getAccessToken(), TraktTv.getInstance().getRefreshToken())) {
      lblTraktStatus.setText(TmmResourceBundle.getString("Settings.trakt.status.good"));
    }
    else {
      lblTraktStatus.setText(TmmResourceBundle.getString("Settings.trakt.status.bad"));
    }

    btnGetTraktPin.addActionListener(e -> getTraktPin());
    btnGetTraktPin.setEnabled(License.getInstance().isValidLicense());
    btnTestTraktConnection.addActionListener(e -> {
      try {
        TraktTv.getInstance().refreshAccessToken();
        TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.trakttv"),
            TmmResourceBundle.getString("Settings.trakt.testconnection.good"));
      }
      catch (Exception e1) {
        TmmToastManager.showErrorToast(this, TmmResourceBundle.getString("Settings.trakttv"),
            TmmResourceBundle.getString("Settings.trakt.testconnection.bad"));
      }
    });
    btnTestTraktConnection.setEnabled(License.getInstance().isValidLicense());

    // data init
    if (License.getInstance().isValidLicense() && StringUtils.isNotBlank(Simkl.getInstance().getAccessToken())) {
      lblSimklStatus.setText(TmmResourceBundle.getString("Settings.simkl.status.good"));
    }
    else {
      lblSimklStatus.setText(TmmResourceBundle.getString("Settings.simkl.status.bad"));
    }

    btnGetSimklPin.addActionListener(e -> getSimklPin());
    btnGetSimklPin.setEnabled(License.getInstance().isValidLicense());
    btnTestSimklConnection.addActionListener(e -> {
      if (Simkl.getInstance().testConnection()) {
        TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.simkl"),
            TmmResourceBundle.getString("Settings.simkl.testconnection.good"));
      }
      else {
        TmmToastManager.showErrorToast(this, TmmResourceBundle.getString("Settings.simkl"),
            TmmResourceBundle.getString("Settings.simkl.testconnection.bad"));
      }
    });
    btnTestSimklConnection.setEnabled(License.getInstance().isValidLicense());
  }

  private void getTraktPin() {
    TraktTv.getInstance().setAccessToken("");
    TraktTv.getInstance().setRefreshToken("");

    TraktTv traktTv = TraktTv.getInstance();

    String deviceCode = "";
    String url;
    // open the pin url in a browser
    try {
      Map<String, String> response = traktTv.getDeviceCode();
      deviceCode = response.get("DEVICE_CODE");
      String authUrl = response.get("AUTH_URL");
      String userCode = response.get("USER_CODE");
      if (StringUtils.isNoneBlank(deviceCode, authUrl, userCode)) {
        url = authUrl + "/" + userCode;
        TmmUIHelper.browseUrl(url);
      }
      else {
        throw new Exception("No verification url and user code received");
      }
    }
    catch (Exception ex) {
      // browser could not be opened, show a dialog box
      TmmToastManager.showErrorToast(this, TmmResourceBundle.getString("Settings.trakttv"),
          TmmResourceBundle.getString("Settings.trakt.getpin.error"));
      LOGGER.error("Error occurred while trying to get Trakt.tv access code - '{}'", ex.getMessage());
      return;
    }

    // let the user insert the pin
    String accessToken = "";
    String refreshToken = "";

    int retryCount = 0;
    while (retryCount < 5) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(TmmResourceBundle.getString("Settings.trakt.getpin.desc")), BorderLayout.NORTH);
      LinkLabel linkLabel = new LinkLabel(url);
      linkLabel.addActionListener(e -> {
        try {
          TmmUIHelper.browseUrl(url);
        }
        catch (Exception ignored) {
          // ignored
        }
      });
      linkLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

      panel.add(linkLabel, BorderLayout.CENTER);
      panel.add(new JLabel(TmmResourceBundle.getString("Settings.trakt.getpin.desc2")), BorderLayout.SOUTH);

      int answer = JOptionPane.showConfirmDialog(MainWindow.getFrame(), panel, TmmResourceBundle.getString("Settings.trakttv"),
          JOptionPane.OK_CANCEL_OPTION);

      // user clicked abort
      if (answer == JOptionPane.OK_OPTION) {
        // try to get access token
        try {
          Map<String, String> tokens = traktTv.getToken(deviceCode);
          accessToken = tokens.get("accessToken") == null ? "" : tokens.get("accessToken");
          refreshToken = tokens.get("refreshToken") == null ? "" : tokens.get("refreshToken");

          if (StringUtils.isNoneBlank(accessToken, refreshToken)) {
            break;
          }

          retryCount++;
          TmmToastManager.showErrorToast(this, TmmResourceBundle.getString("Settings.trakttv"),
              TmmResourceBundle.getString("Settings.trakt.getpin.error"));
        }
        catch (Exception ignored) {
          // ignored
        }
      }
      else if (answer == JOptionPane.CANCEL_OPTION) {
        lblTraktStatus.setText(null);
        return;
      }
    }

    if (StringUtils.isNoneBlank(accessToken, refreshToken)) {
      TraktTv.getInstance().setAccessToken(accessToken);
      TraktTv.getInstance().setRefreshToken(refreshToken);
      lblTraktStatus.setText(TmmResourceBundle.getString("Settings.trakt.status.good"));
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.trakttv"),
          TmmResourceBundle.getString("Settings.trakt.getpin.success"));
    }
    else {
      JOptionPane.showMessageDialog(MainWindow.getFrame(), TmmResourceBundle.getString("Settings.trakt.getpin.problem"),
          TmmResourceBundle.getString("Settings.trakt.getpin"), JOptionPane.ERROR_MESSAGE);
      lblTraktStatus.setText(TmmResourceBundle.getString("Settings.trakt.status.bad"));
    }
  }

  private void getSimklPin() {
    Simkl simkl = Simkl.getInstance();

    String verificationUrl = "";
    String userCode = "";
    long pollIntervalMs = 2000;

    // request a PIN code
    try {
      Map<String, String> response = simkl.getPinCode();
      userCode = response.get("user_code");
      verificationUrl = response.get("verification_uri");
      String intervalStr = response.get("interval");
      if (StringUtils.isNotBlank(intervalStr)) {
        try {
          pollIntervalMs = Math.max(1000, Long.parseLong(intervalStr) * 1000L);
        }
        catch (NumberFormatException ignored) {
          // fall back to the default poll interval
        }
      }
      if (StringUtils.isNoneBlank(userCode, verificationUrl)) {
        TmmUIHelper.browseUrl(verificationUrl + "/" + userCode);
      }
      else {
        throw new Exception("No verification url and user code received");
      }
    }
    catch (Exception ex) {
      // browser could not be opened, show a dialog box
      TmmToastManager.showErrorToast(this, TmmResourceBundle.getString("Settings.simkl"), TmmResourceBundle.getString("Settings.simkl.getpin.error"));
      LOGGER.error("Error occurred while trying to get Simkl access code - '{}'", ex.getMessage());
      return;
    }

    String url = verificationUrl + "/" + userCode;
    String accessToken = "";

    int retryCount = 0;
    while (retryCount < 5) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(TmmResourceBundle.getString("Settings.simkl.getpin.desc")), BorderLayout.NORTH);
      LinkLabel linkLabel = new LinkLabel(url);
      linkLabel.addActionListener(e -> {
        try {
          TmmUIHelper.browseUrl(url);
        }
        catch (Exception ignored) {
          // ignored
        }
      });
      linkLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

      panel.add(linkLabel, BorderLayout.CENTER);
      panel.add(new JLabel(TmmResourceBundle.getString("Settings.simkl.getpin.desc2")), BorderLayout.SOUTH);

      int answer = JOptionPane.showConfirmDialog(MainWindow.getFrame(), panel, TmmResourceBundle.getString("Settings.simkl"),
          JOptionPane.OK_CANCEL_OPTION);

      // user clicked abort
      if (answer == JOptionPane.OK_OPTION) {
        // try to get access token
        try {
          accessToken = simkl.pollForToken(userCode);

          if (StringUtils.isNotBlank(accessToken)) {
            break;
          }

          retryCount++;
          TmmToastManager.showErrorToast(this, TmmResourceBundle.getString("Settings.simkl"),
              TmmResourceBundle.getString("Settings.simkl.getpin.error"));
          try {
            Thread.sleep(pollIntervalMs);
          }
          catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
        catch (Exception ignored) {
          // ignored
        }
      }
      else if (answer == JOptionPane.CANCEL_OPTION) {
        lblSimklStatus.setText(null);
        return;
      }
    }

    if (StringUtils.isNotBlank(accessToken)) {
      simkl.setAccessToken(accessToken);
      lblSimklStatus.setText(TmmResourceBundle.getString("Settings.simkl.status.good"));
      TmmToastManager.showSuccessToast(this, TmmResourceBundle.getString("Settings.simkl"),
          TmmResourceBundle.getString("Settings.simkl.getpin.success"));
    }
    else {
      JOptionPane.showMessageDialog(MainWindow.getFrame(), TmmResourceBundle.getString("Settings.simkl.getpin.problem"),
          TmmResourceBundle.getString("Settings.simkl.getpin"), JOptionPane.ERROR_MESSAGE);
      lblSimklStatus.setText(TmmResourceBundle.getString("Settings.simkl.status.bad"));
    }
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[grow]", "[][15lp!][][15lp!][]"));
    {
      JPanel panelTrakt = new JPanel();
      panelTrakt.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][10lp!][]")); // 16lp ~ width of the

      JLabel lblTraktT = new TmmLabel(TmmResourceBundle.getString("Settings.trakt"), H3);

      if (!License.getInstance().isValidLicense()) {
        lblTraktT.setText("*PRO* " + lblTraktT.getText());
      }

      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelTrakt, lblTraktT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/settings#trakttv"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");
      {
        lblTraktStatus = new JLabel("");
        panelTrakt.add(lblTraktStatus, "cell 1 0 2 1");
      }
      {
        btnGetTraktPin = new JButton(TmmResourceBundle.getString("Settings.trakt.getpin"));
        panelTrakt.add(btnGetTraktPin, "cell 1 1 2 1");

        btnTestTraktConnection = new JButton(TmmResourceBundle.getString("Settings.trakt.testconnection"));
        panelTrakt.add(btnTestTraktConnection, "cell 1 1 2 1");
      }

      JLabel lblTraktDateT = new TmmLabel(TmmResourceBundle.getString("Settings.trakt.date"));
      panelTrakt.add(lblTraktDateT, "flowx,cell 1 3 2 1");

      cbTraktDate = new JComboBox(DateField.values());
      panelTrakt.add(cbTraktDate, "cell 1 3 2 1");
    }
    {
      JPanel panelSimkl = new JPanel();
      panelSimkl.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][]"));

      JLabel lblSimklT = new TmmLabel(TmmResourceBundle.getString("Settings.simkl"), H3);

      if (!License.getInstance().isValidLicense()) {
        lblSimklT.setText("*PRO* " + lblSimklT.getText());
      }

      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelSimkl, lblSimklT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/settings#simkl"));
      add(collapsiblePanel, "cell 0 2,growx, wmin 0");
      {
        lblSimklStatus = new JLabel("");
        panelSimkl.add(lblSimklStatus, "cell 1 0 2 1");
      }
      {
        btnGetSimklPin = new JButton(TmmResourceBundle.getString("Settings.simkl.getpin"));
        panelSimkl.add(btnGetSimklPin, "cell 1 1 2 1");

        btnTestSimklConnection = new JButton(TmmResourceBundle.getString("Settings.simkl.testconnection"));
        panelSimkl.add(btnTestSimklConnection, "cell 1 1 2 1");
      }
    }
    {
      JPanel panelMdbList = new JPanel();
      panelMdbList.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[]"));
      JLabel lblMdbListT = new TmmLabel(TmmResourceBundle.getString("Settings.external.rating.mdblist"), H3);

      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelMdbList, lblMdbListT, true);
      add(collapsiblePanel, "cell 0 4,growx, wmin 0");
      {
        JLabel lblMdbListApiKeyT = new JLabel(TmmResourceBundle.getString("Settings.api.key"));
        panelMdbList.add(lblMdbListApiKeyT, "cell 1 0 2 1");

        tfMdbListApiKey = new JTextField();
        panelMdbList.add(tfMdbListApiKey, "cell 1 0 2 1");
        tfMdbListApiKey.setColumns(30);

        JTextArea tpMediaPlayer = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.external.rating.mdblist.hint"));
        panelMdbList.add(tpMediaPlayer, "cell 1 1 2 1,growx, wmin 0");
        TmmFontHelper.changeFont(tpMediaPlayer, L2);
      }
    }
    initDataBindings();
  }

  protected void initDataBindings() {
    Property settingsBeanProperty = BeanProperty.create("traktDateField");
    Property jComboBoxBeanProperty = BeanProperty.create("selectedItem");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty, cbTraktDate,
        jComboBoxBeanProperty);
    autoBinding.bind();

    Property settingsBeanProperty_1 = BeanProperty.create("mdbListApiKey");
    Property jTextFieldBeanProperty = BeanProperty.create("text");
    AutoBinding autobinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_1, tfMdbListApiKey,
        jTextFieldBeanProperty);
    autobinding_1.bind();
  }
}

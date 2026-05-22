package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.model.User;

@SpringBootTest
public class EmployeeServiceSecurityTest {

  @Mock private DataSource dataSource;

  @Mock private Connection connection;

  @Mock private PreparedStatement preparedStatement;

  @Mock private ResultSet resultSet;

  @InjectMocks private EmployeeService employeeService;

  @BeforeEach
  public void setup() throws SQLException {
    MockitoAnnotations.openMocks(this);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);
  }

  @Test
  public void testFindUserByUsername_UsesPreparedStatement() throws SQLException {
    String username = "testuser";

    employeeService.findUserByUsername(username);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(queryCaptor.capture());

    String capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isEqualTo("SELECT * FROM users WHERE username = ?");
    assertThat(capturedQuery).doesNotContain(username);
  }

  @Test
  public void testFindUserByUsername_BindsParameterSafely() throws SQLException {
    String username = "testuser";

    employeeService.findUserByUsername(username);

    verify(preparedStatement).setString(1, username);
  }

  @Test
  public void testFindUserByUsername_PreventsSqlInjection() throws SQLException {
    String maliciousInput = "admin' OR '1'='1";

    when(resultSet.next()).thenReturn(false);

    List<User> result = employeeService.findUserByUsername(maliciousInput);

    verify(preparedStatement).setString(1, maliciousInput);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(queryCaptor.capture());

    String capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isEqualTo("SELECT * FROM users WHERE username = ?");
    assertThat(capturedQuery).doesNotContain("OR");
    assertThat(capturedQuery).doesNotContain("1=1");

    assertThat(result).isEmpty();
  }

  @Test
  public void testFindUserByUsername_HandlesSpecialCharacters() throws SQLException {
    String usernameWithSpecialChars = "user'; DROP TABLE users; --";

    when(resultSet.next()).thenReturn(false);

    List<User> result = employeeService.findUserByUsername(usernameWithSpecialChars);

    verify(preparedStatement).setString(1, usernameWithSpecialChars);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(queryCaptor.capture());

    String capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isEqualTo("SELECT * FROM users WHERE username = ?");
    assertThat(capturedQuery).doesNotContain("DROP");
    assertThat(capturedQuery).doesNotContain("--");

    assertThat(result).isEmpty();
  }

  @Test
  public void testFindUserByUsername_ReturnsValidUser() throws SQLException {
    String username = "validuser";

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn(username);
    when(resultSet.getString("password")).thenReturn("hashedPassword");
    when(resultSet.getString("email")).thenReturn("user@example.com");

    List<User> result = employeeService.findUserByUsername(username);

    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getUsername()).isEqualTo(username);
    assertThat(result.get(0).getEmail()).isEqualTo("user@example.com");
  }
}

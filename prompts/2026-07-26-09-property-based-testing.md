I want to add property based tests using jqwik. Use version 1.9.3. Use the spring boot extension.
- There should be 4 users in the system (this should be a constant, do this in the setup)
- There should be 3 calendars of priorities default, medium, high (this should be a constant, do this in the setup)
- Using these users and calendars, the property based test should perform different sequences of actions on behalf of the existing users 
   - create-meeting
   - invite someone else to a meeting
   - accept an invitation
   - reject an invitation

Use Independent actions from JQwick because they are faster.

The invariant should always hold true: no user can ever part of two meetings at the same time - no sequence of actions should ever allow this.

If the test fails, do not update the source (do not try to fix it), commit the failing test but ensure that the most shrunk example is in the test output / commit message.

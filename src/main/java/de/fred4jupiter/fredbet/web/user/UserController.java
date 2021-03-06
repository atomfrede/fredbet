package de.fred4jupiter.fredbet.web.user;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import de.fred4jupiter.fredbet.domain.AppUser;
import de.fred4jupiter.fredbet.security.FredBetPermission;
import de.fred4jupiter.fredbet.security.SecurityService;
import de.fred4jupiter.fredbet.service.UserAlreadyExistsException;
import de.fred4jupiter.fredbet.service.UserNotDeletableException;
import de.fred4jupiter.fredbet.service.UserService;
import de.fred4jupiter.fredbet.web.WebMessageUtil;

@Controller
@RequestMapping("/user")
public class UserController {

	private static final String EDIT_USER_PAGE = "user/edit";

	private static final String CREATE_USER_PAGE = "user/create";

	@Autowired
	private UserService userService;

	@Autowired
	private WebMessageUtil messageUtil;

	@Autowired
	private SecurityService securityService;

	@RequestMapping
	public ModelAndView list() {
		List<UserDto> users = userService.findAllAsUserDto();
		return new ModelAndView("user/list", "allUsers", users);
	}

	@RequestMapping("{id}")
	public ModelAndView edit(@PathVariable("id") Long userId) {
		AppUser user = userService.findByUserId(userId);

		EditUserCommand editUserCommand = toEditUserCommand(user);

		return new ModelAndView(EDIT_USER_PAGE, "editUserCommand", editUserCommand);
	}

	private EditUserCommand toEditUserCommand(AppUser appUser) {
		EditUserCommand userCommand = new EditUserCommand();
		userCommand.setUserId(appUser.getId());
		userCommand.setUsername(appUser.getUsername());
		userCommand.setDeletable(appUser.isDeletable());
		if (!CollectionUtils.isEmpty(appUser.getAuthorities())) {
			for (GrantedAuthority grantedAuthority : appUser.getAuthorities()) {
				userCommand.addRole(grantedAuthority.getAuthority());
			}
		}
		return userCommand;
	}

	@PreAuthorize("hasAuthority('" + FredBetPermission.PERM_EDIT_USER + "')")
	@RequestMapping(value = "/edit", method = RequestMethod.POST)
	public ModelAndView edit(@Valid EditUserCommand editUserCommand, BindingResult bindingResult, RedirectAttributes redirect,
			ModelMap modelMap) {
		if (bindingResult.hasErrors()) {
			return new ModelAndView(EDIT_USER_PAGE, "editUserCommand", editUserCommand);
		}

		AppUser updateUser = userService.updateUser(editUserCommand);
		messageUtil.addInfoMsg(redirect, "user.edited", updateUser.getUsername());
		return new ModelAndView("redirect:/user");
	}

	@PreAuthorize("hasAuthority('" + FredBetPermission.PERM_DELETE_USER + "')")
	@RequestMapping("{id}/delete")
	public ModelAndView delete(@PathVariable("id") Long userId, RedirectAttributes redirect) {
		if (securityService.getCurrentUser().getId().equals(userId)) {
			messageUtil.addErrorMsg(redirect, "user.deleted.couldNotDeleteOwnUser");
			return new ModelAndView("redirect:/user");
		}

		AppUser appUser = userService.findByUserId(userId);
		try {
			userService.deleteUser(userId);
			messageUtil.addInfoMsg(redirect, "user.deleted", appUser.getUsername());
		} catch (UserNotDeletableException e) {
			messageUtil.addErrorMsg(redirect, "user.not.deletable", appUser.getUsername());
		}

		return new ModelAndView("redirect:/user");
	}

	@PreAuthorize("hasAuthority('" + FredBetPermission.PERM_CREATE_USER + "')")
	@RequestMapping(value = "/create", method = RequestMethod.GET)
	public String create(@ModelAttribute CreateUserCommand createUserCommand) {
		return CREATE_USER_PAGE;
	}

	@PreAuthorize("hasAuthority('" + FredBetPermission.PERM_CREATE_USER + "')")
	@RequestMapping(method = RequestMethod.POST)
	public ModelAndView create(@Valid CreateUserCommand createUserCommand, BindingResult bindingResult, RedirectAttributes redirect,
			ModelMap modelMap) {
		if (bindingResult.hasErrors()) {
			return new ModelAndView(CREATE_USER_PAGE, "createUserCommand", createUserCommand);
		}

		try {
			userService.createUser(createUserCommand);
		} catch (UserAlreadyExistsException e) {
			messageUtil.addErrorMsg(modelMap, "user.username.duplicate");
			return new ModelAndView(CREATE_USER_PAGE, "createUserCommand", createUserCommand);
		}

		messageUtil.addInfoMsg(redirect, "user.created", createUserCommand.getUsername());
		return new ModelAndView("redirect:/user");
	}

}

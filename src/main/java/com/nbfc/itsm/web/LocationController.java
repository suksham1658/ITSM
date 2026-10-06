package com.nbfc.itsm.web;

import com.nbfc.itsm.admin.LocationService;
import com.nbfc.itsm.domain.Location;
import com.nbfc.itsm.exception.ItsmException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Locations master: list/add/edit/delete (LOCATION_MANAGE), plus a JSON lookup used by the IMAC raise form. */
@Controller
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/admin/locations")
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public String list(Model model) {
        model.addAttribute("nav", "adminLocations");
        model.addAttribute("pageTitle", "Locations");
        model.addAttribute("locations", locationService.all());
        return "admin/locations";
    }

    @PostMapping("/admin/locations")
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public String create(@RequestParam("name") String name, @RequestParam(value = "address", required = false) String address,
                         @RequestParam(value = "sortOrder", defaultValue = "0") int sortOrder, RedirectAttributes ra) {
        try {
            Location l = locationService.create(name, address, sortOrder);
            ra.addFlashAttribute("message", "Location \"" + l.getName() + "\" added.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/locations";
    }

    @PostMapping("/admin/locations/{id}")
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public String update(@PathVariable Long id, @RequestParam("name") String name,
                         @RequestParam(value = "address", required = false) String address,
                         @RequestParam(value = "active", defaultValue = "false") boolean active,
                         @RequestParam(value = "sortOrder", defaultValue = "0") int sortOrder, RedirectAttributes ra) {
        try {
            locationService.update(id, name, address, active, sortOrder);
            ra.addFlashAttribute("message", "Location updated.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/locations";
    }

    @PostMapping("/admin/locations/{id}/delete")
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            locationService.delete(id);
            ra.addFlashAttribute("message", "Location deleted.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/locations";
    }

    /** IMAC raise form: on selecting a location, return its address and the hostname it will get. */
    @GetMapping("/locations/{id}/info")
    @PreAuthorize("hasAuthority('TICKET_CREATE')")
    @ResponseBody
    public Map<String, String> info(@PathVariable Long id) {
        Location l = locationService.get(id);
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("name", l.getName());
        out.put("address", l.getAddress() == null ? "" : l.getAddress());
        out.put("hostname", locationService.peekHostname(l.getName()));
        return out;
    }
}
